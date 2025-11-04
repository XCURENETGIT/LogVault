package com.xcurenet.logvault.module.task.process;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.fs.FileProcessor;
import com.xcurenet.logvault.module.analysis.AnalysisService;
import com.xcurenet.logvault.module.analysis.KeywordAnalysis;
import com.xcurenet.logvault.module.analysis.PrivacyAnalysis;
import com.xcurenet.logvault.module.task.service.TaskMessage;
import com.xcurenet.logvault.module.task.service.TaskProcessor;
import com.xcurenet.logvault.opensearch.EmassDoc;
import com.xcurenet.logvault.opensearch.IndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.jasypt.commons.CommonUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

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

	@Override
	public boolean supports(String taskType) {
		return "OCR".equalsIgnoreCase(taskType);
	}

	@Override
	public void process(TaskMessage message) throws Exception {
		EmassDoc doc = mapper.readValue(message.getData(), EmassDoc.class);
		log.debug("{}", doc);
		List<EmassDoc.Attach> attaches = doc.getAttach();
		if (attaches == null || attaches.isEmpty()) return;

		boolean changed = false;
		for (EmassDoc.Attach attach : attaches) {
			String ext = FilenameUtils.getExtension(attach.getName());
			if (attach.isExist() && (conf.getOcrTargetExt().contains(attach.getExpectedExtension()) || conf.getOcrTargetExt().contains(ext))) {
				log.info("[OCR_START] {} | {} | {}", message.getMsgId(), attach.getId(), attach.getName());
				try (InputStream in = fileProcessor.open(attach.getPath())) {
					String text = ocrText(in, attach.getName());
					log.info("[OCR_TEXT] {} | {} | {} | {}", message.getMsgId(), attach.getId(), attach.getName(), Common.getSummaryText(text));
					attach.setText(Common.nvl(attach.getText()) + "\n" + text);
					attach.setOcrStatus("S");
					changed = true;
				} catch (Exception e) {
					log.warn("[OCR_WARN] {} | {} | {}", message.getMsgId(), attach.getId(), e.getMessage());
					attach.setOcrStatus("E");
				}
			}
		}

		if (changed) {
			keywordAnalysis.detect(doc);                // 키워드 탐지
			privacyAnalysis.detect(doc);                // 개인정보 탐지
		}

		//키워드, 개인정보 탐지, OCR 처리 상태 색인용도
		String index = conf.getIndexName() + doc.getCtime().substring(0, 8);
		indexService.index(doc, doc.getMsgid(), index);
	}

	private String ocrText(final InputStream in, final String fileName) throws IOException {
		Connection.Response res = Jsoup.connect(conf.getOcrApiUrl()).timeout(60_000).method(Connection.Method.POST).ignoreContentType(true).data("api_key", conf.getOcrApiKey())   // form-data 필드
				.data("type", "upload").data("textout", "true").data("boxes_type", "line").data("image", fileName, in).execute();
		JSONObject data = JSONObject.parseObject(res.body());
		return data.getJSONObject("result").getString("full_text");
	}
}
