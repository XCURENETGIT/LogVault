package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.regex.MatchResult;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.crypto.Crypto;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.loader.PatternLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import com.xcurenet.logvault.privacy.PrivacyPattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class PrivacyAnalysis {
	private final PrivacyPattern pattern;
	private final Config conf;

	public void detect(final ScanData scanData) {
		if (scanData == null || scanData.getEmassDoc() == null) return;

		try {
			detect(scanData.getEmassDoc());
		} catch (Exception e) {
			log.warn("REG_ERROR | {}", e.getMessage(), e);
		}
	}

	public void detect(final EmassDoc doc) {
		if (Common.isNotEquals(doc.getService().getSvc3(), "S")) return; // 발신 데이터만 처리

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
		if (Common.isEmpty(text)) return 0;

		StopWatch sw = DateUtils.start();
		StringBuilder sb = new StringBuilder();
		List<EmassDoc.PrivacyInfo> bucket = ensurePrivacyInfoList(doc);

		int added = 0;
		Map<String, List<MatchResult>> api = pattern.scan(text);
		log.debug("REG_DATA | {}", api);
		if (api != null) {
			for (String key : api.keySet()) {
				List<MatchResult> arr = api.get(key);
				if (arr == null || arr.isEmpty()) continue;

				EmassDoc.PrivacyInfo info = toPrivacyInfo(key, type, attachName, arr, /*enforceDetectCode=*/true);
				if (info == null) continue;

				bucket.add(info);
				added += info.getCount();
				sb.append(key).append(":").append(info.getCount()).append(" ");
			}
		} else log.warn("REG_DATA | DATA IS NULL");

		if (Common.isNotEmpty(sb.toString())) {
			log.info("REG_DONE | {} | {} | {}", type, sb.toString(), DateUtils.stop(sw));
		}
		doc.setPrivacyInfo(bucket);
		return added;
	}

	/**
	 * 허용된 key이고(옵션), 임계치(코드 값 or 기본1) 이상일 때만 PrivacyInfo 생성
	 *
	 * @param enforceDetectCode true면 PatternLoader.isDetectCode(key) 검사, false면 검사하지 않음(로컬 패턴용)
	 */
	private EmassDoc.PrivacyInfo toPrivacyInfo(String key, String type, String attachName, List<MatchResult> arr, boolean enforceDetectCode) {
		if (enforceDetectCode && !PatternLoader.isDetectCode(key)) return null; // 주민번호, 카드번호 등 사용하는 항목만

		List<String> items = new ArrayList<>();
		for (MatchResult it : arr) {
			if (it == null || it.matchString() == null) continue;

			String matchString = it.matchString();
			if (enforceDetectCode) matchString = encString(matchString.getBytes(StandardCharsets.UTF_8)); //개인 정보 탐지 텍스트는 암호화 처리
			items.add(matchString);
		}
		if (items.isEmpty()) return null;

		// 임계치는 detectCode 검사 미적용 시에도 동일하게 적용
		int threshold = PatternLoader.getCodeValueOrDefault(key, 1);
		if (items.size() < threshold) return null;

		log.debug("items : {}", items);
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

	private String encString(byte[] text) {
		try {
			Crypto crypto = new Crypto(conf.getEncryptKey(), conf.getEncyptCipher());
			byte[] cipherTextBytes = crypto.encrypt(text, 0, text.length);
			return Base64.getEncoder().encodeToString(cipherTextBytes);
		} catch (Exception e) {
			log.warn("ENC_ERROR | {}", e.getMessage(), e);
		}
		return null;
	}
}
