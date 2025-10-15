package com.xcurenet.logvault.module.analysis;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.xcurenet.common.regex.MatchResult;
import com.xcurenet.common.regex.PatternDetector;
import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.loader.PatternLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.*;

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
		int total = processText(doc, doc.getBody() == null ? null : doc.getBody().getText(), "B", "-");
		if (doc.getAttach() != null) {
			for (EmassDoc.Attach a : doc.getAttach()) {
				total += processText(doc, a.getText(), "A", a.getName());
			}
		}
		if (total == 0) {
			doc.setPrivacyInfo(null);
		}
		doc.setPrivacyTotal(total);
	}

	/**
	 * 텍스트 1개를 처리하고, 생성된 항목 수를 반환
	 */
	private int processText(EmassDoc doc, String text, String type, String attachName) {
		if (CommonUtil.isEmpty(text)) return 0;

		StringBuilder sb = new StringBuilder();
		List<EmassDoc.PrivacyInfo> bucket = ensurePrivacyInfoList(doc);
		int added = 0;
		// 1) API 결과 처리 — detectCode 검사 적용
		JSONObject api = callPrivacyApi(text);
		if (api != null) {
			JSONObject data = api.getJSONObject("data");
			if (data != null && !data.isEmpty()) {

				for (String key : data.keySet()) {
					JSONArray arr = data.getJSONArray(key);
					if (arr == null || arr.isEmpty()) continue;

					EmassDoc.PrivacyInfo info = toPrivacyInfo(key, type, attachName, arr, /*enforceDetectCode=*/true);
					if (info == null) continue;
					bucket.add(info);
					added += info.getCount();
					sb.append(key).append(":").append(info.getCount()).append(" ");
				}
			}
		}

		// 2) 로컬 패턴 결과 처리 — detectCode 검사 미적용
		Map<String, List<MatchResult>> local = runLocalPatterns(text);
		if (local != null && !local.isEmpty()) {
			for (Map.Entry<String, List<MatchResult>> e : local.entrySet()) {
				String key = e.getKey();
				List<MatchResult> matches = e.getValue();
				if (matches == null || matches.isEmpty()) continue;

				JSONArray arr = new JSONArray(matches.size());
				for (MatchResult m : matches) {
					JSONObject o = new JSONObject();
					o.put("start", m.start());
					o.put("end", m.end());
					o.put("matchString", m.match());
					arr.add(o);
				}

				EmassDoc.PrivacyInfo info = toPrivacyInfo(key, type, attachName, arr, /*enforceDetectCode=*/false);
				if (info == null) continue;
				bucket.add(info);
				added += info.getCount();
				sb.append(key).append(":").append(info.getCount()).append(" ");
			}
		}
		if(CommonUtil.isNotEmpty(sb.toString())) {
			log.info("[REG_DONE] {} | {} | {}", doc.getMsgid(), type, sb.toString());
		}
		return added;
	}

	private Map<String, List<MatchResult>> runLocalPatterns(String text) {
		try {
			PatternDetector detector = PatternLoader.getUserCodeMap();
			return detector.detectAll(text);
		} catch (Exception e) {
			log.warn("[LOCAL_PRIVACY] {} | {}", CommonUtil.getSummaryText(text), e.toString());
			return Collections.emptyMap();
		}
	}

	private JSONObject callPrivacyApi(String text) {
		for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
			try {
				return restClient.post().uri(conf.getPrivacyAnalysisUrl()).contentType(TEXT_PLAIN_UTF8).body(text).retrieve().body(JSONObject.class);
			} catch (Exception e) {
				log.warn("[PRIVACY_API] {} | ({}/{}) | {}", CommonUtil.getSummaryText(text), attempt, MAX_RETRIES, e.getMessage());
				if (attempt < MAX_RETRIES) CommonUtil.sleep(RETRY_SLEEP_MS);
			}
		}
		return null;
	}

	/**
	 * 허용된 key이고(옵션), 임계치(코드 값 or 기본1) 이상일 때만 PrivacyInfo 생성
	 *
	 * @param enforceDetectCode true면 PatternLoader.isDetectCode(key) 검사, false면 검사하지 않음(로컬 패턴용)
	 */
	private EmassDoc.PrivacyInfo toPrivacyInfo(String key, String type, String attachName, JSONArray arr, boolean enforceDetectCode) {
		if (enforceDetectCode && !PatternLoader.isDetectCode(key)) return null;

		List<EmassDoc.PrivacyData> items = new ArrayList<>(arr.size());
		for (int i = 0; i < arr.size(); i++) {
			JSONObject it = arr.getJSONObject(i);
			if (it == null) continue;
			items.add(EmassDoc.PrivacyData.builder().start(it.getInteger("start")).end(it.getInteger("end")).match(it.getString("matchString")).build());
		}
		if (items.isEmpty()) return null;

		// 임계치는 detectCode 검사 미적용 시에도 동일하게 적용(원치 않으면 분기 가능)
		int threshold = PatternLoader.getCodeValueOrDefault(key, 1);
		if (items.size() < threshold) return null;

		EmassDoc.PrivacyInfo info = new EmassDoc.PrivacyInfo();
		info.setId(key);
		info.setType(type);
		info.setAttachName(attachName);
		info.setPrivacyData(items);
		info.setCount(items.size());
		return info;
	}

	private static List<EmassDoc.PrivacyInfo> ensurePrivacyInfoList(EmassDoc doc) {
		if (doc.getPrivacyInfo() == null) doc.setPrivacyInfo(new ArrayList<>());
		return doc.getPrivacyInfo();
	}
}
