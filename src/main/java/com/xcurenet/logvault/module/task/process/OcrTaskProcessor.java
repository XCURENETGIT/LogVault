package com.xcurenet.logvault.module.task.process;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.fs.FileProcessor;
import com.xcurenet.logvault.module.analysis.KeywordAnalysis;
import com.xcurenet.logvault.module.analysis.PrivacyAnalysis;
import com.xcurenet.logvault.module.task.service.TaskMessage;
import com.xcurenet.logvault.module.task.service.TaskProcessor;
import com.xcurenet.logvault.opensearch.EmassDoc;
import com.xcurenet.logvault.opensearch.IndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * OCR 처리를 담당하는 Processor
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class OcrTaskProcessor implements TaskProcessor {
	private final Config conf;
	private final ObjectMapper mapper;
	private final FileProcessor fileProcessor;
	protected final IndexService indexService;
	private final KeywordAnalysis keywordAnalysis;
	private final PrivacyAnalysis privacyAnalysis;
	private final RestTemplate restTemplate = new RestTemplate();

	@Override
	public boolean supports(String taskType) {
		return "OCR".equalsIgnoreCase(taskType);
	}

	@Override
	public void process(TaskMessage message) throws Exception {
		MDC.put("worker", Thread.currentThread().getName());
		try {
			EmassDoc doc = mapper.readValue(message.getData(), EmassDoc.class);
			MDC.put("msgId", doc.getMsgid());

			log.debug("{}", doc);
			List<EmassDoc.Attach> attaches = doc.getAttach();
			if (attaches == null || attaches.isEmpty()) return;

			StopWatch sw1 = DateUtils.start();
			int success = 0;
			int fail = 0;
			int target = 0;
			for (EmassDoc.Attach attach : attaches) {
				String ext = FilenameUtils.getExtension(attach.getName());
				if (attach.isExist() && (conf.getOcrTargetExt().contains(attach.getExpectedExtension()) || conf.getOcrTargetExt().contains(ext))) {
					target++;
					StopWatch sw = DateUtils.start();

					log.info("OCR_START | {}", conf.getDestPathSmall(attach.getPath()));
					try (InputStream in = fileProcessor.open(attach.getPath())) {
						String text = ocrText(in, attach.getName());

						log.info("OCR_TEXT | {} | {} | {}", conf.getDestPathSmall(attach.getPath()), Common.nvl(text).length(), DateUtils.stop(sw));

						attach.setText(Common.nvl(attach.getText()) + "\n" + text);
						attach.setOcrStatus("S");
						attach.setOcrRate(sw.getTotalTimeMillis());
						success++;
					} catch (Exception e) {
						log.warn("OCR_WARN | {} | {}", conf.getDestPathSmall(attach.getPath()), conf.getOcrApiUrl(), e);
						attach.setOcrStatus("E");
						fail++;
					}
				}
			}

			if (success > 0) {
				//키워드, 개인정보 탐지 재 처리를 위해 초기화
				doc.setKeywordInfo(null);
				doc.setPrivacyInfo(null);
				doc.setPrivacyTotal(0);

				keywordAnalysis.detect(doc);                // 키워드 탐지
				privacyAnalysis.detect(doc);                // 개인정보 탐지
			}

			//키워드, 개인정보 탐지, OCR 처리 상태 색인용도
			String index = conf.getIndexName() + doc.getCtime().substring(0, 8);
			indexService.index(doc, index);
			log.info("OCR_END | CNT:{}(FAIL:{}/OK:{}) | {}\n", target, fail, success, DateUtils.stop(sw1));
		} finally {
			MDC.remove("msgId");
		}
	}

	/**
	 * Local OCR
	 *
	 * @param in       첨부파일 InputStream
	 * @param fileName 첨부파일명
	 * @param filePath 첨부파일 경로
	 * @return 첨부 텍스트
	 */
	private String ocrTextLocal(InputStream in, String fileName, String filePath) throws IOException {
		byte[] imageBytes = IOUtils.toByteArray(in);
		String base64Image = Base64.getEncoder().encodeToString(imageBytes);

		Map<String, Object> payload = new HashMap<>();

		Map<String, Object> imageUrl = new HashMap<>();
		imageUrl.put("url", "data:image/png;base64," + base64Image);

		List<Object> contentList = new ArrayList<>();
		contentList.add(Map.of("type", "image_url", "image_url", imageUrl));
		contentList.add(Map.of("type", "text", "text", "Extract only the visible text from the image.\n" +
				"Do not add, modify, translate, summarize, or analyze anything.\n" +
				"Return the extracted text exactly as it appears, line by line."));

		Map<String, Object> message = new HashMap<>();
		message.put("role", "user");
		message.put("content", contentList);

		payload.put("model", "/models/allenai/olmOCR-2-7B-1025-FP8");
		payload.put("messages", List.of(message));
		payload.put("max_tokens", 1500);
		payload.put("temperature", 0.0);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));

		HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(payload), headers);

		String url = "http://10.100.20.209:8001/v1/chat/completions";

		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
		if (!resp.getStatusCode().is2xxSuccessful()) {
			throw new IOException("HTTP " + resp.getStatusCodeValue() + " : " + resp.getBody());
		}

		JSONObject json = JSONObject.parseObject(resp.getBody());
		assert json != null;
		return json.getJSONArray("choices")
				.getJSONObject(0)
				.getJSONObject("message")
				.getString("content");
	}

	/**
	 * Synap OCR
	 *
	 * @param in       첨부파일 InputStream
	 * @param fileName 첨부파일명
	 * @param filePath 첨부파일 경로
	 * @return 첨부 텍스트
	 */
	private String ocrText(final InputStream in, final String fileName) throws IOException {
		Connection.Response res = Jsoup.connect(conf.getOcrApiUrl())
				.timeout(conf.getOcrTimeout())
				.method(Connection.Method.POST)
				.ignoreContentType(true)
				.data("api_key", conf.getOcrApiKey())
				.data("type", "upload")
				.data("textout", "true")
				.data("boxes_type", "line")
				.data("image", fileName, in).execute();
		JSONObject data = JSONObject.parseObject(res.body());
		return data.getJSONObject("result").getString("full_text");
	}
}
