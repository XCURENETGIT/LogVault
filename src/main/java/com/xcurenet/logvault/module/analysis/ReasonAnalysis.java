package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class ReasonAnalysis {
	private final PrivacyAnalysis privacyAnalysis;

	/* =========================
	 * 차단인 경우 사유가 들어온다.
	 * 1;2;abced==,3;2;abced==,5;2;abced==,5;2;abced==,5;2;abced==,5;2;abced==,5;2;abced==,2;2;abced==,8;3;abced==,6;2;abced==,9;3;abced==
	 * id, confidence, detectStr(base54)
	 * ========================= */
	public void setReason(final ScanData data) {
		MSGData msg = data.getMsgData();
		if (msg.getDetections() == null) return;

		log.info("DETECTIONS | {}", msg.getDetections());

		EmassDoc doc = data.getEmassDoc();
		List<String> items = Common.split(msg.getDetections(), ",");
		for (String item : items) {
			List<String> reason = Common.split(item, ";");
			if (reason.size() != 3) continue;

			int id = Common.nvz(reason.get(0));
			int confidence = Common.nvz(reason.get(1));
			String detectStr = Common.nvl(reason.get(2));

			appendPrivacy(doc, id, confidence, detectStr);
			appendKeyword(doc, id, detectStr);
		}

		if (doc.getPrivacyInfo() != null && !doc.getPrivacyInfo().isEmpty()) {
			int total = doc.getPrivacyInfo().stream().mapToInt(EmassDoc.PrivacyInfo::getCount).sum();
			doc.setPrivacyTotal(total);
		}
		if (doc.getKeywordInfo() != null && !doc.getKeywordInfo().getKeywords().isEmpty()) {
			doc.setKeywordTotal(doc.getKeywordInfo().getKeywords().size());
			int sum = doc.getKeywordInfo().getKeywords().stream().mapToInt(EmassDoc.KeywordInfo.Keyword::getCount).sum();
			doc.setKeywordTotal(sum);
		}
	}

	private void appendKeyword(EmassDoc doc, int id, String detectStr) {
		if (id < 9) return;

		String keyword = Common.decodeBase64ToString(detectStr);
		EmassDoc.KeywordInfo keywordInfo = doc.getKeywordInfo();
		if (keywordInfo == null) keywordInfo = new EmassDoc.KeywordInfo();

		List<EmassDoc.KeywordInfo.Keyword> body = keywordInfo.getBody();
		if (body == null) body = new ArrayList<>();

		EmassDoc.KeywordInfo.Keyword exist = null;
		for (EmassDoc.KeywordInfo.Keyword k : body) {
			if (keyword.equals(k.getName())) {
				exist = k;
				break;
			}
		}

		if (exist == null) body.add(EmassDoc.KeywordInfo.Keyword.builder().name(keyword).count(1).build());
		else exist.setCount(exist.getCount() + 1);

		keywordInfo.setExist(true);
		keywordInfo.setBody(body);
		keywordInfo.setKeywords(body);

		doc.setKeywordInfo(keywordInfo);
	}

	private void appendPrivacy(EmassDoc doc, int id, int confidence, String detectStr) {
		if (id > 8) return;

		String encrypted = privacyAnalysis.encString(Common.decodeBase64ToString(detectStr).getBytes(StandardCharsets.UTF_8));
		String piId = getId(id);
		List<EmassDoc.PrivacyInfo> privacyInfos = doc.getPrivacyInfo();
		if (doc.getPrivacyInfo() == null) privacyInfos = new ArrayList<>();

		EmassDoc.PrivacyInfo info = getPrivacyInfo(privacyInfos, piId);
		if (info == null) {
			info = new EmassDoc.PrivacyInfo();
			info.setId(piId);
			info.setType("B");
			info.setAttachName("-");
			info.setPrivacyData(new ArrayList<>(List.of(encrypted)));
			info.setCount(info.getPrivacyData().size());
			privacyInfos.add(info);
		} else {
			info.getPrivacyData().add(encrypted);
			info.setCount(info.getPrivacyData().size());
		}
		doc.setPrivacyInfo(privacyInfos);
	}

	private EmassDoc.PrivacyInfo getPrivacyInfo(List<EmassDoc.PrivacyInfo> privacyInfos, String piId) {
		for (EmassDoc.PrivacyInfo info : privacyInfos) {
			if (info.getId().equals(piId)) {
				return info;
			}
		}
		return null;
	}

	private String getId(int id) {
		return switch (id) {
			case 1 -> "SN";
			case 2 -> "CN";
			case 3 -> "MN";
			case 4 -> "EML";
			case 5 -> "BA";
			case 6 -> "PN";
			case 7 -> "DN";
			case 8 -> "SSN";
			default -> "-";
		};
	}
}
