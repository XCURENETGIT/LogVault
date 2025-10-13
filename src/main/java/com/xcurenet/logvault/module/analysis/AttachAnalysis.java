package com.xcurenet.logvault.module.analysis;

import com.alibaba.fastjson2.JSONObject;
import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.common.utils.FileUtil;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class AttachAnalysis {
	private final RestClient restClient = RestClient.create();
	private final Config conf;

	private JSONObject getText(final String msgId, final String filePath, final String fileName) {
		LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("msgId", msgId);
		body.add("filePath", filePath);
		body.add("fileName", fileName);
		body.add("extractImage", "false");
		body.add("checkArchiveImage", "false");
		body.add("checkArchiveDepth", "5");
		body.add("checkExcelHiddenSheet", "false");

		int maxRetries = 3;
		int attempt = 0;
		while (attempt < maxRetries) {
			try {
				attempt++;
				return restClient.post().uri(conf.getFileAnalysisUrl()).contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(JSONObject.class);
			} catch (Exception e) {
				log.warn("[GET_TEXT] {} | ({}/{}) | {}", filePath, attempt, maxRetries, e.getMessage());
				if (attempt < maxRetries) CommonUtil.sleep(1000);
			}
		}
		return null;
	}

	public void setAttachText(final ScanData msg) {
		EmassDoc doc = msg.getEmassDoc();
		List<EmassDoc.Attach> attaches = doc.getAttach();
		if (attaches == null) return;
		for (EmassDoc.Attach attach : attaches) {
			if (!attach.isExist()) continue;

			long startTime = System.currentTimeMillis();
			JSONObject text = getText(doc.getMsgid(), attach.getSrcPath(), attach.getName());
			if (text != null && text.getBoolean("success")) {
				JSONObject data = text.getJSONObject("data");
				String limit = CommonUtil.limitLength(data.getString("text"), conf.getTextLimitLength());
				limit = CommonUtil.limitTokenLengthWithSpace(limit, conf.getTextLimitToken());

				attach.setText(limit);
				attach.setExpectedExtension(data.getString("extension"));
				attach.setExpectedUnknown(data.getBoolean("unknownType"));
				log.info("[ATT_TEXT] {} | {} | {} | {} | {}", doc.getMsgid(), attach.getSrcPath(), text.get("success"), DateUtils.duration(startTime), CommonUtil.getSummaryText(attach.getText()));
			} else {
				log.warn("[ATT_TEXT] {} | {} | {} | {} | {}", doc.getMsgid(), attach.getSrcPath(), text, DateUtils.duration(startTime), CommonUtil.getSummaryText(attach.getText()));
			}
		}
	}
}
