package com.xcurenet.logvault.module.task.process;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.alert.AlertService;
import com.xcurenet.logvault.module.task.service.TaskDispatcherService;
import com.xcurenet.logvault.module.task.service.TaskMessage;
import com.xcurenet.logvault.module.task.service.TaskProcessor;
import com.xcurenet.logvault.opensearch.EmassDoc;
import com.xcurenet.logvault.opensearch.IndexService;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * ML_ANALYSIS 처리를 담당하는 Processor
 */
@Log4j2
@Component
public class MLTaskProcessor implements TaskProcessor {
	private final Config conf;
	private final ObjectMapper mapper;
	private final RestTemplate restTemplate;
	protected final IndexService indexService;
	private final AlertService alertService;

	public MLTaskProcessor(Config conf, ObjectMapper mapper, @Qualifier("mlRestTemplate") RestTemplate restTemplate, IndexService indexService, AlertService alertService) {
		this.conf = conf;
		this.mapper = mapper;
		this.restTemplate = restTemplate;
		this.indexService = indexService;
		this.alertService = alertService;
	}

	@Override
	public boolean supports(String taskType) {
		return TaskDispatcherService.TASK_TYPE.ML.name().equalsIgnoreCase(taskType);
	}

	@Override
	public void process(TaskMessage message) throws Exception {
		MDC.put("worker", Thread.currentThread().getName());
		EmassDoc doc = null;
		try {
			doc = mapper.readValue(message.getData(), EmassDoc.class);
			MDC.put("msgId", doc.getMsgid());

			setBodyMLResult(doc);
			setAttachMLResult(doc);
			setSummaryMLResult(doc);
		} catch (Exception e) {
			log.warn("{} | {}", ErrorCode.ML_ANALYSIS_ERROR, e.toString());
			throw e;
		} finally {
			if (doc != null) {
				updateIndex(doc);
				processAlert(doc);
			}
			MDC.remove("msgId");
		}
	}

	/**
	 * EmassDoc 문서에 프롬프트 분석 내용 업데이트
	 */
	private void setBodyMLResult(EmassDoc doc) {
		try {
			if (doc.getBody() != null && doc.getBody().getText() != null) {
				EmassDoc.MLResult mlResult = analysisML(Common.limitLength(doc.getBody().getText(), conf.getMlApiTextLimit()));
				log.info("ML__TASK | BODY: {}", mlResult);
				if (mlResult != null) doc.getBody().setMlResult(mlResult);
			}
		} catch (Exception e) {
			log.warn("{} | {} | {}", ErrorCode.ML_ANALYSIS_BODY_ERROR.toString(), doc.getBody().getSize(), e.toString());
		}
	}

	/**
	 * EmassDoc 문서에 첨부 분석 내용 업데이트
	 */
	private void setAttachMLResult(EmassDoc doc) {
		if (doc.getAttach() == null) return;
		for (EmassDoc.Attach attach : doc.getAttach()) {
			try {
				if (attach.getText() != null) {
					EmassDoc.MLResult mlResult = analysisML(Common.limitLength(attach.getText(), conf.getMlApiTextLimit()));
					log.info("ML__TASK | ATTACH: {}", mlResult);
					if (mlResult != null) attach.setMlResult(mlResult);
				}
			} catch (Exception e) {
				log.warn("{} | {} | {} | {}", ErrorCode.ML_ANALYSIS_ATTACH_ERROR, attach.getSize(), conf.getDestPathSmall(attach.getPath()), e.toString());
			}
		}
	}

	/**
	 * EmassDoc 문서에 프롬프트, 첨부 분석 내용을 취합한 내용을 업데이트
	 */
	private void setSummaryMLResult(EmassDoc doc) {
		EmassDoc.MLResult summary = new EmassDoc.MLResult();
		int result = 200;
		String message = "OK";
		if (doc.getBody() != null) {
			EmassDoc.MLResult mlResult = doc.getBody().getMlResult();
			summary.merge(mlResult);

			if (mlResult != null) {
				result = doc.getBody().getMlResult().getResult();
				message = doc.getBody().getMlResult().getMessage();
			}
		}

		if (doc.getAttach() != null) {
			for (EmassDoc.Attach attach : doc.getAttach()) {
				EmassDoc.MLResult mlResult = attach.getMlResult();
				summary.merge(mlResult);
				if (mlResult != null && mlResult.getResult() > 200) {
					result = mlResult.getResult();
					message = mlResult.getMessage();
				}
			}
		}
		summary.setResult(result);
		summary.setMessage(message);
		doc.setMlResult(summary);
	}


	/**
	 * ML 결과 내용 색인
	 */
	private void updateIndex(EmassDoc doc) {
		doc.setProcessStatus(getProcessStatus(doc));
		indexService.index(doc);
	}

	/**
	 * ML완료 후, Alert 로직 실행
	 */
	private void processAlert(final EmassDoc doc) {
		alertService.send(doc);
	}

	/**
	 * ML완료 후, 상태 필드 END로 업데이트
	 */
	private EmassDoc.ProcessStatus getProcessStatus(EmassDoc doc) {
		EmassDoc.ProcessStatus status = doc.getProcessStatus() == null ? EmassDoc.ProcessStatus.builder().build() : doc.getProcessStatus();
		status.setMl("E");
		return status;
	}


	/**
	 * ML API 호출
	 */
	private EmassDoc.MLResult analysisML(final String text) {
		JSONObject data = new JSONObject();
		data.put("text", Common.toBase64(text.getBytes()));
		data.put("code_split_threshold", conf.getMlCodeSplitThreshold());
		data.put("codeline_exist_threshold", conf.getMlCodelineExistThreshold());
		data.put("detect_model_dir", conf.getMlDetectModelDir());

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));

		log.debug("ML_API_CALL | {}", data.toJSONString());
		HttpEntity<String> entity = new HttpEntity<>(data.toJSONString(), headers);
		ResponseEntity<String> resp = restTemplate.postForEntity(conf.getMlApiUrl(), entity, String.class);
		if (!resp.getStatusCode().is2xxSuccessful()) {
			log.warn("ML_API_ERROR | {} | {}", resp.getStatusCode(), resp.getBody());
			return null;
		}

		log.debug("{}", resp.getBody());
		JSONObject body = JSONObject.parseObject(resp.getBody());
		if (body != null) {
			EmassDoc.MLResult mlResult = new EmassDoc.MLResult();
			mlResult.setCodeExist(body.getBoolean("code_exist"));
			mlResult.setProbs(body.getFloat("probs"));
			mlResult.setCategory(body.getInteger("class"));
			mlResult.setKeywords(body.getJSONArray("keywords").toJavaList(String.class));
			mlResult.setResult(body.getInteger("result"));
			mlResult.setMessage(body.getString("message"));
			return mlResult;
		}
		return null;
	}
}
