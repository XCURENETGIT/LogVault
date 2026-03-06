package com.xcurenet.logvault.module.analysis;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.loader.PatternLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class PrivacyAIAnalysis {

	private final Config conf;

	// ML API가 반환하는 키(탐지 타입)
	private static final String[] PI_TYPE = {"SN", "DN", "AN", "PN", "MN", "BN", "EML", "SSN"};

	// 긴 텍스트 split 처리
	private static final int CHUNK_SIZE = 10_000;
	private static final int OVERLAP = 200; // 경계 걸림 누락 방지
	private static final int STEP = CHUNK_SIZE - OVERLAP;

	// HTTP client (pooling / keep-alive / HTTP2 가능)
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();

	public void detect(final ScanData scanData) {
		if (scanData == null || scanData.getEmassDoc() == null) {
			log.warn("{}", ErrorCode.PRIVACY_MSGDATA_NULL.toString());
			return;
		}
		try {
			detect(scanData.getEmassDoc());
		} catch (Exception e) {
			log.warn("{} | {}", ErrorCode.PRIVACY_UNKNOWN_ERROR.toString(), e.getMessage(), e);
		}
	}

	public void detect(final EmassDoc doc) {
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

	private static List<EmassDoc.PrivacyInfo> ensurePrivacyInfoList(EmassDoc doc) {
		if (doc.getPrivacyInfo() == null) {
			doc.setPrivacyInfo(new ArrayList<>());
		}
		return doc.getPrivacyInfo();
	}

	private int processText(EmassDoc doc, String text, String type, String attachName) {
		if (Common.isEmpty(text)) return 0;

		StopWatch sw = DateUtils.start();
		StringBuilder sb = new StringBuilder();
		List<EmassDoc.PrivacyInfo> bucket = ensurePrivacyInfoList(doc);
		int added = 0;
		JSONObject api = detectPII(text);
		if (api == null) return 0;

		for (String key : PI_TYPE) {
			JSONArray datas = api.getJSONArray(key);
			if (datas == null || datas.isEmpty()) continue;

			EmassDoc.PrivacyInfo info = toPrivacyInfo(key, type, attachName, datas);
			if (info == null) continue;

			bucket.add(info);
			added += info.getCount();
			sb.append(key).append(":").append(info.getCount()).append(" ");
		}
		if (Common.isNotEmpty(sb.toString())) {
			log.info("REG_DONE | {} | {} | TEXT.LENGTH:{} | {}", type, sb.toString(), text.length(), DateUtils.stop(sw));
		} else {
			log.info("REG_DONE | {} | PII_NONE | TEXT.LENGTH:{} | {}", type, text.length(), DateUtils.stop(sw));
		}
		doc.setPrivacyInfo(bucket);
		return added;
	}

	private EmassDoc.PrivacyInfo toPrivacyInfo(String key, String type, String attachName, JSONArray datas) {
		if (!PatternLoader.isDetectCode(key)) {
			log.debug("REG_INFO | {} | KEY:{}", ErrorCode.PRIVACY_DETECT_CODE_INVALID.toString(), key);
			return null;
		}

		List<String> items = new ArrayList<>();
		for (int i = 0; i < datas.size(); i++) {
			JSONObject it = datas.getJSONObject(i);
			if (it == null) continue;

			String ms = it.getString("matchString");
			if (Common.isEmpty(ms)) continue;

			String encrypted = Common.encString(ms.getBytes(StandardCharsets.UTF_8), conf.getEncryptKey(), conf.getEncyptCipher());

			if (encrypted != null) {
				items.add(encrypted);
			}
		}

		if (items.isEmpty()) return null;

		int threshold = PatternLoader.getCodeValueOrDefault(key, 1);
		if (items.size() < threshold) {
			log.debug("REG_INFO | {} | KEY:{} COUNT:{} THRESHOLD:{}", ErrorCode.PRIVACY_THRESHOLD_NOT_MET.toString(), key, items.size(), threshold);
			return null;
		}

		EmassDoc.PrivacyInfo info = new EmassDoc.PrivacyInfo();
		info.setId(key);
		info.setType(type);
		info.setAttachName(attachName);
		info.setPrivacyData(new ArrayList<>(items));
		info.setCount(items.size());
		return info;
	}

	public JSONObject detectPII(String text) {
		return detectPII(text, 5000);
	}

	public JSONObject detectPII(String text, int max) {
		try {
			JSONObject param = new JSONObject();
			param.put("text", text);
			param.put("max_results_per_type", max);
			byte[] body = JSON.toJSONBytes(param, JSONWriter.Feature.LargeObject);

			HttpURLConnection conn = (HttpURLConnection) new URL(conf.getMlPrivacyApiUrl()).openConnection();
			conn.setRequestMethod("POST");
			conn.setConnectTimeout(60000);
			conn.setReadTimeout(60000);
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			try (OutputStream os = conn.getOutputStream()) {
				os.write(body);
				os.flush();
			}

			if (conn.getResponseCode() == 200) {
				String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				log.debug("ML_PRIVACY_API_RESPONSE | {}", response);
				JSONObject obj = JSONObject.parseObject(response);
				if (obj.getBoolean("success")) {
					return obj.getJSONObject("data");
				}
				log.warn("{} | TEXT.LENGTH:{} | {}", ErrorCode.PRIVACY_ML_API_ERROR.toString(), text.length(), response);
			} else {
				log.warn("REG_INFO | RESPONSE ERROR | {} | TEXT.LENGTH:{} | {}", ErrorCode.PRIVACY_ML_API_ERROR.toString(), text.length(), conn.getResponseCode());
			}
		} catch (Exception e) {
			log.error("{} | TEXT.LENGTH:{}", ErrorCode.PRIVACY_ML_API_ERROR.toString(), text.length(), e);
		}
		return null;
	}
}