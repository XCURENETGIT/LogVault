package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.utils.CollectionUtil;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.KeywordLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class KeywordAnalysis {

	private final KeywordLoader keywordLoader;

	public void detect(final ScanData scanData) {
		detect(scanData.getEmassDoc());
	}

	public void detect(final EmassDoc doc) {
		if (Common.isNotEquals(doc.getService().getSvc3(), "S")) return; // 발신 데이터만 처리

		EmassDoc.Body body = doc.getBody();
		EmassDoc.KeywordInfo keywordInfo = new EmassDoc.KeywordInfo();

		// 본문 키워드 탐지
		if (body != null && Common.isNotEmpty(body.getText())) {
			keywordInfo.setBody(checkKeyword(body.getText()));
		}

		// 첨부파일 키워드 탐지
		if (doc.getAttach() != null && CollectionUtil.isNotEmpty(doc.getAttach())) {
			List<EmassDoc.KeywordInfo.Keyword> attachNameKeywords = new ArrayList<>();
			List<EmassDoc.KeywordInfo.Keyword> attachTextKeywords = new ArrayList<>();
			for (EmassDoc.Attach attach : doc.getAttach()) {
				List<EmassDoc.KeywordInfo.Keyword> name = checkKeyword(attach.getName());
				if (name != null) attachNameKeywords.addAll(name);

				List<EmassDoc.KeywordInfo.Keyword> text = checkKeyword(attach.getText());
				if (text != null) attachTextKeywords.addAll(text);
			}
			log.debug("KEYWORD_ATT_NAME | {}", attachNameKeywords);
			log.debug("KEYWORD_ATT | {}", attachTextKeywords);

			keywordInfo.setAttachName(attachNameKeywords.isEmpty() ? null : attachNameKeywords);
			keywordInfo.setAttach(attachTextKeywords.isEmpty() ? null : attachTextKeywords);
		}

		// 전체 존재 여부
		keywordInfo.setExist(CollectionUtil.isNotEmpty(keywordInfo.getBody()) || CollectionUtil.isNotEmpty(keywordInfo.getAttachName()) || CollectionUtil.isNotEmpty(keywordInfo.getAttach()));

		// 병합된 keywords 생성
		if (keywordInfo.isExist()) {
			List<EmassDoc.KeywordInfo.Keyword> keywords = new ArrayList<>();
			if (CollectionUtil.isNotEmpty(keywordInfo.getBody())) keywords.addAll(keywordInfo.getBody());
			if (CollectionUtil.isNotEmpty(keywordInfo.getAttachName())) keywords.addAll(keywordInfo.getAttachName());
			if (CollectionUtil.isNotEmpty(keywordInfo.getAttach())) keywords.addAll(keywordInfo.getAttach());

			// 중복 키워드 count 합산
			Map<String, Integer> merged = new LinkedHashMap<>();
			for (EmassDoc.KeywordInfo.Keyword k : keywords) {
				merged.merge(k.getName(), k.getCount(), Integer::sum);
			}

			List<EmassDoc.KeywordInfo.Keyword> mergedList = new ArrayList<>();
			for (Map.Entry<String, Integer> entry : merged.entrySet()) {
				mergedList.add(EmassDoc.KeywordInfo.Keyword.builder().name(entry.getKey()).count(entry.getValue()).build());
			}
			keywordInfo.setKeywords(mergedList);
		}
		doc.setKeywordInfo(keywordInfo);
	}

	private List<EmassDoc.KeywordInfo.Keyword> checkKeyword(final String keyword) {
		if (keyword == null) return null;

		List<EmassDoc.KeywordInfo.Keyword> result = new ArrayList<>();
		Map<String, Integer> keywords = keywordLoader.KEYWORD_MATCHER_REF.get().checkKeywordOverMin(keyword);
		for (String key : keywords.keySet()) {
			result.add(EmassDoc.KeywordInfo.Keyword.builder().name(key).count(keywords.get(key)).build());
		}
		return result.isEmpty() ? null : result;
	}
}
