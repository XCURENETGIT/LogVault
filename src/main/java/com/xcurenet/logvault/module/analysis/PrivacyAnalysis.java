package com.xcurenet.logvault.module.analysis;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class PrivacyAnalysis {

	private static final int MAX_RETRIES = 3;
	private static final long RETRY_SLEEP_MS = 1_000L;
	private static final MediaType TEXT_PLAIN_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

	private final Config conf;
	private final RestClient restClient = RestClient.create();

	public void detect(final ScanData scanData) {
		if (scanData == null || scanData.getEmassDoc() == null) return;
		EmassDoc doc = scanData.getEmassDoc();
		int total = setPrivacyBody(doc);
		total += setPrivacyAttach(doc);
		doc.setPrivacyTotal(total);
	}

	private int setPrivacyAttach(final EmassDoc doc) {
		if (doc.getAttach() == null) return 0;
		int total = 0;
		for (EmassDoc.Attach attach : doc.getAttach()) {
			if (CommonUtil.isEmpty(attach.getText())) continue;

			JSONObject res = requestPrivacy(attach.getText());
			if (res == null) continue;

			JSONObject data = res.getJSONObject("data");
			if (data == null || data.isEmpty()) continue;


			List<EmassDoc.PrivacyInfo> infos = new ArrayList<>();
			for (Map.Entry<String, Object> e : data.entrySet()) {
				String key = e.getKey();
				JSONArray arr = data.getJSONArray(key);
				if (arr == null || arr.isEmpty()) continue;

				EmassDoc.PrivacyInfo info = toPrivacyInfo(key, "A", attach.getName(), arr);
				infos.add(info);
				total += info.getCount();
			}

			List<EmassDoc.PrivacyInfo> privacyInfos = doc.getPrivacyInfo();
			if (privacyInfos != null) privacyInfos.addAll(infos);
			else doc.setPrivacyInfo(infos);
		}
		return total;
	}

	private int setPrivacyBody(final EmassDoc doc) {
		EmassDoc.Body body = doc.getBody();
		if (body == null || !CommonUtil.isNotEmpty(body.getText())) return 0;

		JSONObject res = requestPrivacy(body.getText());
		if (res == null) return 0;

		JSONObject data = res.getJSONObject("data");
		if (data == null || data.isEmpty()) return 0;

		int total = 0;
		List<EmassDoc.PrivacyInfo> infos = new ArrayList<>();
		for (Map.Entry<String, Object> e : data.entrySet()) {
			String key = e.getKey();
			JSONArray arr = data.getJSONArray(key);
			if (arr == null || arr.isEmpty()) continue;

			EmassDoc.PrivacyInfo info = toPrivacyInfo(key, "B", "-", arr);
			infos.add(info);
			total += info.getCount();
		}
		doc.setPrivacyInfo(infos);
		return total;
	}

	private JSONObject requestPrivacy(final String text) {
		for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
			try {
				return restClient.post().uri(conf.getPrivacyAnalysisUrl()).contentType(TEXT_PLAIN_UTF8).body(text).retrieve().body(JSONObject.class);
			} catch (Exception e) {
				log.warn("[PRIVACY] {} | ({}/{}) | {}", CommonUtil.getSummaryText(text), attempt, MAX_RETRIES, e.getMessage());
				if (attempt < MAX_RETRIES) CommonUtil.sleep(RETRY_SLEEP_MS);
			}
		}
		return null;
	}

	private EmassDoc.PrivacyInfo toPrivacyInfo(String key, String type, String attachName, JSONArray arr) {
		List<EmassDoc.PrivacyData> items = new ArrayList<>(arr.size());
		for (int i = 0; i < arr.size(); i++) {
			JSONObject it = arr.getJSONObject(i);
			if (it == null) continue;
			items.add(EmassDoc.PrivacyData.builder().start(it.getInteger("start")).end(it.getInteger("end")).match(it.getString("matchString")).build());
		}
		EmassDoc.PrivacyInfo info = new EmassDoc.PrivacyInfo();
		info.setId(key);                 // 예: "SN"
		info.setType(type);              // 본문, 첨부내용
		info.setAttachName(attachName);  // 첨부파일 이름
		info.setPrivacyData(items);
		info.setCount(items.size());
		return info;
	}
}
