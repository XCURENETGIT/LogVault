package com.xcurenet.logvault.module.task.process;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.common.utils.FileUtil;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.fs.FileProcessor;
import com.xcurenet.logvault.module.alert.AlertService;
import com.xcurenet.logvault.module.analysis.KeywordAnalysis;
import com.xcurenet.logvault.module.analysis.PrivacyAIAnalysis;
import com.xcurenet.logvault.module.task.service.TaskDispatcherService;
import com.xcurenet.logvault.module.task.service.TaskMessage;
import com.xcurenet.logvault.module.task.service.TaskMessageRepository;
import com.xcurenet.logvault.module.task.service.TaskProcessor;
import com.xcurenet.logvault.opensearch.EmassDoc;
import com.xcurenet.logvault.opensearch.IndexService;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OCR 처리를 담당하는 Processor
 */
@Log4j2
@Service
public class OcrTaskProcessor implements TaskProcessor {
	private static final String URL = "http://%s:%s";
	private final Config conf;
	private final ObjectMapper mapper;
	private final FileProcessor fileProcessor;
	protected final IndexService indexService;
	private final KeywordAnalysis keywordAnalysis;
	private final PrivacyAIAnalysis privacyAnalysis;
	private final TaskMessageRepository repository;
	private final RestTemplate restTemplate;
	private final AlertService alertService;

	private static final String OCR_STATUS_SUCCESS = "S";
	private static final String OCR_STATUS_ERROR = "E";

	public OcrTaskProcessor(Config conf, ObjectMapper mapper, FileProcessor fileProcessor, IndexService indexService, KeywordAnalysis keywordAnalysis, PrivacyAIAnalysis privacyAnalysis, TaskMessageRepository repository, @Qualifier("ocrRestTemplate") RestTemplate restTemplate, AlertService alertService) {
		this.conf = conf;
		this.mapper = mapper;
		this.fileProcessor = fileProcessor;
		this.indexService = indexService;
		this.keywordAnalysis = keywordAnalysis;
		this.privacyAnalysis = privacyAnalysis;
		this.repository = repository;
		this.restTemplate = restTemplate;
		this.alertService = alertService;
	}

	@Override
	public boolean supports(String taskType) {
		return TaskDispatcherService.TASK_TYPE.OCR.name().equalsIgnoreCase(taskType);
	}

	@Override
	public void process(TaskMessage message) throws Exception {
		MDC.put("worker", Thread.currentThread().getName());
		EmassDoc doc = null;
		try {
			doc = mapper.readValue(message.getData(), EmassDoc.class);
			MDC.put("msgId", doc.getMsgid());

			log.debug("Processing document: {}", doc);
			List<EmassDoc.Attach> attaches = doc.getAttach();
			if (attaches == null || attaches.isEmpty()) {
				log.info("No attachments found for msgId: {}", doc.getMsgid());
				return;
			}

			StopWatch sw1 = DateUtils.start();
			OcrResult result = processAttachments(attaches);
			if (result.successCount > 0) {
				reanalyzeDocument(doc);
			}

			updateIndex(doc);
			log.info("OCR__END | Target: {} (Fail: {}/Success: {}) | Total Time: {}", result.targetCount, result.failCount, result.successCount, DateUtils.stop(sw1));
		} catch (Exception e) {
			log.error("An error occurred during OCR task processing for message: {}", message.getMsgId(), e);
		} finally {
			if (doc != null) {
				insertMLTask(doc);
				processAlert(doc);
			}
			MDC.remove("msgId");
		}
	}

	private static class OcrResult {
		int successCount = 0;
		int failCount = 0;
		int targetCount = 0;
	}

	/**
	 * 첨부파일 OCR 실행
	 */
	private OcrResult processAttachments(List<EmassDoc.Attach> attaches) {
		OcrResult result = new OcrResult();
		for (EmassDoc.Attach attach : attaches) {
			if (!attach.isExist() || !attach.isOcrTarget()) continue;
			if (isOcrTargetFile(attach)) { // 1. 첨부 파일 자체가 이미지인 경우 OCR 처리
				result.targetCount++;
				processAttachment(attach, result);
			} else if (conf.isOcrEmbeddedImageEnable()) {// 2. 파일 내 포함된 이미지 처리
				processEmbeddedImages(attach, result);
			}
		}
		return result;
	}

	/**
	 * OCR 대상이 되는 파일인지 여부 (확장자 검사)
	 */
	private boolean isOcrTargetFile(EmassDoc.Attach attach) {
		String ext = FileUtil.getExtension(attach.getName());
		return conf.getOcrTargetExt().contains(attach.getExpectedExtension()) || conf.getOcrTargetExt().contains(ext);
	}

	/**
	 * 첨부파일 OCR REST PAI 호출 및 결과 값 업데이트
	 */
	private void processAttachment(EmassDoc.Attach attach, OcrResult result) {
		String pathSmall = conf.getDestPathSmall(attach.getPath());
		StopWatch sw = DateUtils.start();
		log.info("OCR_START | {} | {} Byte | {}", attach.getExtension(), attach.getSize(), pathSmall);

		try (InputStream in = fileProcessor.open(attach.getPath())) {
			String text = process(in, attach.getName());
			attach.setText(Common.nvl(attach.getText()) + "\n" + text);
			attach.setOcrStatus(OCR_STATUS_SUCCESS);
			attach.setOcrRate(sw.getTotalTimeMillis());
			result.successCount++;

			log.info("OCR_TEXT | {} | Length: {} | Time: {}", pathSmall, Common.nvl(text).length(), DateUtils.stop(sw));
		} catch (Exception e) {
			handleOcrException(attach, result, pathSmall, sw, e);
		}
	}

	/**
	 * 첨부파일내 객체이미지 OCR REST PAI 호출 및 결과 값 업데이트
	 */
	private void processEmbeddedImages(EmassDoc.Attach attach, OcrResult result) {
		try {
			List<EmassDoc.ImageExtractorInfo> imageExtractorInfoList = attach.getImageExtractorInfo();
			if (imageExtractorInfoList == null) return;

			for (EmassDoc.ImageExtractorInfo extractorInfo : imageExtractorInfoList) {
				if (extractorInfo.getPath() == null) continue;

				try {
					if (Files.size(Paths.get(extractorInfo.getPath())) > conf.getOcrLimitSize()) continue;
				} catch (IOException e) {
					log.warn("Failed to check embedded image size for path: {}", extractorInfo.getPath(), e);
					continue;
				}

				result.targetCount++;
				processSingleEmbeddedImage(attach, extractorInfo, result);
			}
		} catch (Exception e) {
			log.warn("OCR_WARN (Embedded Overall) | Attach: {} | Error: {}", conf.getDestPathSmall(attach.getPath()), e.getMessage(), e);
			attach.setOcrStatus(OCR_STATUS_ERROR);
			result.failCount++;
		}
	}

	private void processSingleEmbeddedImage(EmassDoc.Attach attach, EmassDoc.ImageExtractorInfo extractorInfo, OcrResult result) {
		StopWatch sw = DateUtils.start();
		String pathSmall = conf.getDestPathSmall(attach.getPath());

		try (InputStream in = fileProcessor.open(extractorInfo.getPath())) {
			String text = process(in, extractorInfo.getName());
			attach.setText(Common.nvl(attach.getText()) + "\n" + text); // 이미지가 여러 개이므로 append
			attach.setOcrStatus(OCR_STATUS_SUCCESS); // 성공 상태 업데이트
			attach.setOcrRate(sw.getTotalTimeMillis());
			result.successCount++;

			log.info("OCR_EMBED | Attach: {} | Embedded: {} | Length: {} | Time: {}", pathSmall, conf.getDestPathSmall(extractorInfo.getPath()), Common.nvl(text).length(), DateUtils.stop(sw));
		} catch (Exception e) {
			handleOcrException(attach, result, pathSmall, sw, e);
		}
	}

	/**
	 * OCR 에러 발생 시 에러값 업데이트
	 */
	private void handleOcrException(EmassDoc.Attach attach, OcrResult result, String pathSmall, StopWatch sw, Exception e) {
		log.warn("OCR_WARN | Path: {} | API: {}:{} | Error: {}", pathSmall, conf.getOcrApiHost(), conf.getOcrApiPort(), e.getMessage(), e);
		attach.setOcrStatus(OCR_STATUS_ERROR);
		attach.setOcrRate(sw.getTotalTimeMillis());
		result.failCount++;
	}

	/**
	 * OCR 결과값으로 키워드, 개인정보 탐지하여 필드 업데이트
	 */
	private void reanalyzeDocument(EmassDoc doc) {
		// 키워드, 개인정보 탐지 재 처리를 위해 초기화
		doc.setKeywordInfo(null);
		doc.setPrivacyInfo(null);
		doc.setPrivacyTotal(0);

		StopWatch swKeyword = DateUtils.start();
		keywordAnalysis.detect(doc);                // 키워드 탐지
		log.info("KWD__END | Time: {}", DateUtils.stop(swKeyword));

		StopWatch swPrivacy = DateUtils.start();
		privacyAnalysis.detect(doc);                // 개인정보 탐지
		log.info("PII__END | Time: {}", DateUtils.stop(swPrivacy));
	}

	/**
	 * OCR 처리 후 색인 용도
	 */
	private void updateIndex(EmassDoc doc) {
		doc.setProcessStatus(getProcessStatus(doc));
		indexService.index(doc);
	}

	private EmassDoc.ProcessStatus getProcessStatus(EmassDoc doc) {
		EmassDoc.ProcessStatus status = doc.getProcessStatus() == null ? EmassDoc.ProcessStatus.builder().build() : doc.getProcessStatus();
		status.setOcr("E");
		return status;
	}

	/**
	 * 처리 완료 후 ML을 사용하는 환경이면 ML TASK 등록
	 */
	private void insertMLTask(final EmassDoc doc) {
		if (conf.isMlApiEnable() && Common.isEquals(doc.getService().getSvc3(), "S")) {
			StopWatch sw1 = DateUtils.start();
			TaskMessage message = new TaskMessage();
			message.setMsgId(doc.getMsgid());
			message.setTaskType(TaskDispatcherService.TASK_TYPE.ML.name());
			message.setData(JSON.toJSONString(doc));
			repository.insertMessage(message);

			log.info("PPS_SEND | {} | Time: {}", TaskDispatcherService.TASK_TYPE.ML.name(), DateUtils.stop(sw1));
		}
	}

	/**
	 * ML을 사용하지 않으면, Alert 로직 실행
	 */
	private void processAlert(final EmassDoc doc) {
		if (!conf.isMlApiEnable()) {
			alertService.send(doc);
		}
	}


	/**
	 * OCR 모듈 분기 처리 (OCR TYPE (SY: Synap OCR, LG: Local GPU, LC:Local CPU))
	 */
	private String process(final InputStream in, final String fileName) throws Exception {
		if (Common.isEquals(conf.getOcrApiType(), "LC")) {
			return ocrTextLocalCPU(in, fileName);
		}
		if (Common.isEquals(conf.getOcrApiType(), "LG")) {
			String base64Image = Base64.getEncoder().encodeToString(IOUtils.toByteArray(in));
			return ocrTextLocalGPU(base64Image);
		}
		if (Common.isEquals(conf.getOcrApiType(), "SY")) {
			return ocrTextSynap(in, fileName);
		}
		return null;
	}

	/**
	 * LOCAL CPU 기반 OCR REST API
	 */
	private String ocrTextLocalCPU(final InputStream in, final String fileName) throws IOException, RestClientException {
		byte[] bytes = in.readAllBytes();
		ByteArrayResource fileAsResource = new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return fileName;
			}
		};

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", fileAsResource);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));

		String url = String.format(URL, conf.getOcrApiHost(), conf.getOcrApiPort()) + "/ocr";
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		ResponseEntity<String> resp = restTemplate.postForEntity(url, requestEntity, String.class);
		if (!resp.getStatusCode().is2xxSuccessful()) {
			throw new IOException("HTTP " + resp.getStatusCode() + " : " + resp.getBody());
		}
		JSONObject json = JSONObject.parseObject(resp.getBody());
		if (json == null) {
			throw new IOException("OCR API response format is invalid or empty.");
		}
		return json.getString("text");
	}

	/**
	 * LOCAL GPU 기반 OCR REST API
	 */
	private String ocrTextLocalGPU(final String base64Image) throws IOException, RestClientException {
		Map<String, Object> imageUrl = Map.of("url", "data:image/png;base64," + base64Image);

		// 프롬프트 구성
		List<Object> contentList = List.of(Map.of("type", "image_url", "image_url", imageUrl), Map.of("type", "text", "text", "Extract only the visible text from the image.\n" + "Do not add, modify, translate, summarize, or analyze anything.\n" + "Return the extracted text exactly as it appears, line by line."));
		Map<String, Object> message = Map.of("role", "user", "content", contentList);

		Map<String, Object> payload = new HashMap<>();
		payload.put("model", conf.getOcrApiLocalGpuModel());
		payload.put("messages", List.of(message));
		payload.put("max_tokens", 1500);
		payload.put("temperature", 0.0);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));

		String url = String.format(URL, conf.getOcrApiHost(), conf.getOcrApiPort()) + "/v1/chat/completions";
		HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(payload), headers);
		ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
		if (!resp.getStatusCode().is2xxSuccessful()) {
			throw new IOException("HTTP " + resp.getStatusCode() + " : " + resp.getBody());
		}

		JSONObject json = JSONObject.parseObject(resp.getBody());
		if (json == null || json.getJSONArray("choices") == null || json.getJSONArray("choices").isEmpty()) {
			throw new IOException("OCR API response format is invalid or empty.");
		}
		return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
	}


	/**
	 * SYNAP GPU 기반 OCR REST API
	 */
	private String ocrTextSynap(final InputStream in, final String fileName) throws IOException {
		String url = String.format(URL, conf.getOcrApiHost(), conf.getOcrApiPort()) + "/sdk/ocr";
		Connection.Response res = Jsoup.connect(url).timeout(conf.getOcrTimeoutSec() * 1000) //Timeout
				.method(Connection.Method.POST) //Method
				.ignoreContentType(true) //ignore content-type
				.data("api_key", conf.getOcrApiKey()) // api key
				.data("type", "upload") //type
				.data("textout", "true")  //textout
				.data("boxes_type", "line")  //boxes type
				.data("image", fileName, in)  //image
				.execute();
		JSONObject data = JSONObject.parseObject(res.body());
		return data.getJSONObject("result").getString("full_text");
	}
}