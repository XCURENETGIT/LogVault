package com.xcurenet.logvault.module.task.process;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.conf.Config;
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

	public MLTaskProcessor(Config conf, ObjectMapper mapper, @Qualifier("mlRestTemplate") RestTemplate restTemplate, IndexService indexService) {
		this.conf = conf;
		this.mapper = mapper;
		this.restTemplate = restTemplate;
		this.indexService = indexService;
	}

	private static final String ML_STATUS_SUCCESS = "S";
	private static final String ML_STATUS_ERROR = "E";

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
			log.warn("{} | {} | {}", ErrorCode.ML_ANALYSIS_ERROR, ErrorCode.fromCode(ErrorCode.ML_ANALYSIS_ERROR), e.toString());
		} finally {
			if (doc != null) updateIndex(doc);
			MDC.remove("msgId");
		}
	}

	private void setBodyMLResult(EmassDoc doc) {
		try {
			if (doc.getBody() != null && doc.getBody().getText() != null) {
				EmassDoc.MLResult mlResult = analysisML(Common.limitLength(doc.getBody().getText(), conf.getMlApiTextLimit()));
				log.info("ML__TASK | BODY: {}", mlResult);
				if (mlResult != null) doc.getBody().setMlResult(mlResult);
			}
		} catch (Exception e) {
			log.warn("{} | {} | {} | {}", ErrorCode.ML_ANALYSIS_BODY_ERROR, ErrorCode.fromCode(ErrorCode.ML_ANALYSIS_BODY_ERROR), doc.getBody().getSize(), e.toString());
		}
	}

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
				log.warn("{} | {} | {} | {} | {}", ErrorCode.ML_ANALYSIS_ATTACH_ERROR, ErrorCode.fromCode(ErrorCode.ML_ANALYSIS_ATTACH_ERROR), attach.getSize(), conf.getDestPathSmall(attach.getPath()), e.toString());
			}
		}
	}

	private void setSummaryMLResult(EmassDoc doc) {
		EmassDoc.MLResult summary = new EmassDoc.MLResult();
		if (doc.getBody() != null) {
			EmassDoc.MLResult mlResult = doc.getBody().getMlResult();
			summary.merge(mlResult);
		}

		if (doc.getAttach() != null) {
			for (EmassDoc.Attach attach : doc.getAttach()) {
				EmassDoc.MLResult mlResult = attach.getMlResult();
				summary.merge(mlResult);
			}
		}
		doc.setMlResult(summary);
	}


	private void updateIndex(EmassDoc doc) {
		//ML 분석 결과 색인 용도
		String index = conf.getIndexName() + doc.getCtime().substring(0, 8);
		doc.setProcessStatus(getProcessStatus(doc));
		indexService.index(doc, index);
	}

	private EmassDoc.ProcessStatus getProcessStatus(EmassDoc doc) {
		EmassDoc.ProcessStatus status = doc.getProcessStatus() == null ? EmassDoc.ProcessStatus.builder().build() : doc.getProcessStatus();
		status.setMl("E");
		return status;
	}

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
			return mlResult;
		}
		return null;
	}
}
