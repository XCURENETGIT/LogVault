package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.utils.CollectionUtil;
import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.logvault.loader.type.KeywordData;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Log4j2
@Service
@RequiredArgsConstructor
public class KeywordAnalysis {

	private final KeywordData keywordData;

	public void detect(final ScanData scanData) {
		EmassDoc doc = scanData.getEmassDoc();
		if (CommonUtil.isNotEquals(doc.getService().getSvc3(), "S")) return; // 발신 데이터만 처리

		EmassDoc.Body body = doc.getBody();
		EmassDoc.KeywordInfo keywordInfo = new EmassDoc.KeywordInfo();

		// ✅ 본문 키워드 탐지
		if (body != null && CommonUtil.isNotEmpty(body.getText())) {
			List<String> datas = checkKeyword(body.getText());
			keywordInfo.setBody(datas.isEmpty() ? null : datas);
		}

		// ✅ 첨부파일 키워드 탐지
		if (doc.getAttach() != null && CollectionUtil.isNotEmpty(doc.getAttach())) {
			Set<String> attachNameKeywords = new HashSet<>();
			Set<String> attachTextKeywords = new HashSet<>();
			for (EmassDoc.Attach attach : doc.getAttach()) {
				attachNameKeywords.addAll(checkKeyword(attach.getName()));
				attachTextKeywords.addAll(checkKeyword(attach.getText()));
			}
			log.debug("[KEYWORD_ATT_NAME] {}", attachNameKeywords);
			log.debug("[KEYWORD_ATT] {}", attachTextKeywords);
			keywordInfo.setAttachName(attachNameKeywords.isEmpty() ? null : new ArrayList<>(attachNameKeywords));
			keywordInfo.setAttach(attachTextKeywords.isEmpty() ? null : new ArrayList<>(attachTextKeywords));
		}
		keywordInfo.setExist(keywordInfo.getBody() != null || keywordInfo.getAttachName() != null || keywordInfo.getAttach() != null);
		if (keywordInfo.isExist()) {
			Set<String> keywords = new HashSet<>();
			if (CollectionUtil.isNotEmpty(keywordInfo.getBody())) keywords.addAll(keywordInfo.getBody());
			if (CollectionUtil.isNotEmpty(keywordInfo.getAttachName())) keywords.addAll(keywordInfo.getAttachName());
			if (CollectionUtil.isNotEmpty(keywordInfo.getAttach())) keywords.addAll(keywordInfo.getAttach());
			keywordInfo.setKeywords(new ArrayList<>(keywords));
		}
		doc.setKeywordInfo(keywordInfo);
	}

	private List<String> checkKeyword(final String keyword) {
		if (keyword == null) return new ArrayList<>();

		Set<String> keywords = keywordData.checkKeyword(keyword);
		if (CollectionUtil.isEmpty(keywords)) return new ArrayList<>();
		return new ArrayList<>(keywords);
	}
}
