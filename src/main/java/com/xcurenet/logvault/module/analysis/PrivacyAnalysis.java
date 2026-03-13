package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.regex.MatchResult;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.loader.PatternLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import com.xcurenet.logvault.privacy.PrivacyPattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class PrivacyAnalysis {

	private final PrivacyPattern pattern;
	private final Config conf;
	private final RestClient restClient;

	/* =========================
	 * Entry
	 * ========================= */
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

	/* =========================
	 * Document Detect
	 * ========================= */
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

	/* =========================
	 * Text Processing
	 * ========================= */
	private int processText(EmassDoc doc, String text, String type, String attachName) {
		if (Common.isEmpty(text)) return 0;

		StopWatch sw = DateUtils.start();
		StringBuilder sb = new StringBuilder();
		List<EmassDoc.PrivacyInfo> bucket = ensurePrivacyInfoList(doc);

		int added = 0;
		Map<String, List<MatchResult>> api;

		try {
			api = pattern.scan(text);
		} catch (Exception e) {
			log.warn("{} | text.length={} | {}", ErrorCode.PRIVACY_REGEX_SCAN_FAIL.toString(), text.length(), e.getMessage(), e);
			return 0;
		}

		log.debug("REG_DATA | {}", api);

		if (api == null) {
			log.warn("{}", ErrorCode.PRIVACY_REGEX_RESULT_NULL.toString());
			return 0;
		}

		if (!api.isEmpty()) {
			for (String key : api.keySet()) {
				List<MatchResult> arr = api.get(key);
				if (arr == null || arr.isEmpty()) continue;

				EmassDoc.PrivacyInfo info = toPrivacyInfo(key, type, attachName, arr);

				if (info == null) continue;

				bucket.add(info);
				added += info.getCount();
				sb.append(key).append(":").append(info.getCount()).append(" ");
			}
		}

		if (Common.isNotEmpty(sb.toString())) {
			log.info("REG_DONE | {} | {} | {}", type, sb.toString(), DateUtils.stop(sw));
		}

		doc.setPrivacyInfo(bucket);
		return added;
	}

	/* =========================
	 * PrivacyInfo Builder
	 * ========================= */
	private EmassDoc.PrivacyInfo toPrivacyInfo(String key, String type, String attachName, List<MatchResult> arr) {
		if (!PatternLoader.isDetectCode(key)) {
			log.debug("{} | KEY:{}", ErrorCode.PRIVACY_DETECT_CODE_INVALID.toString(), key);
			return null;
		}

		List<String> items = new ArrayList<>();
		for (MatchResult it : arr) {
			if (it == null || it.matchString() == null) continue;

			String encrypted = Common.encString(it.matchString().getBytes(StandardCharsets.UTF_8), conf.getEncryptKey(), conf.getEncyptCipher());
			if (encrypted != null) {
				items.add(encrypted);
			}
		}

		if (items.isEmpty()) return null;

		int threshold = PatternLoader.getCodeValueOrDefault(key, 1);
		if (items.size() < threshold) {
			log.debug("{} | KEY:{} COUNT:{} THRESHOLD:{}", ErrorCode.PRIVACY_THRESHOLD_NOT_MET.toString(), key, items.size(), threshold);
			return null;
		}

		EmassDoc.PrivacyInfo info = new EmassDoc.PrivacyInfo();
		info.setId(key);
		info.setType(type);
		info.setAttachName(attachName);
		info.setPrivacyData(items);
		info.setCount(items.size());
		return info;
	}

	private static List<EmassDoc.PrivacyInfo> ensurePrivacyInfoList(EmassDoc doc) {
		if (doc.getPrivacyInfo() == null) {
			doc.setPrivacyInfo(new ArrayList<>());
		}
		return doc.getPrivacyInfo();
	}

}
