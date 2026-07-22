package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.CollectionUtil;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.KeywordLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Log4j2
@Service
@RequiredArgsConstructor
public class KeywordAnalysis {

	private final KeywordLoader keywordLoader;

	public void detect(final ScanData scanData) {
		if (scanData == null || scanData.getEmassDoc() == null) {
			log.warn("{} | scanData or emassDoc is null", ErrorCode.KEYWORD_MSGDATA_NULL.toString());
			return;
		}
		detect(scanData.getEmassDoc());
	}

	public void detect(final EmassDoc doc) {
		if (doc == null) {
			log.warn("{} | EmassDoc is null", ErrorCode.KEYWORD_MSGDATA_NULL.toString());
			return;
		}
		EmassDoc.Body body = doc.getBody();
		EmassDoc.KeywordInfo keywordInfo = doc.getKeywordInfo();
		if (keywordInfo == null) keywordInfo = new EmassDoc.KeywordInfo();
		Set<String> blockReasonKeywords = collectBlockReasonKeywords(keywordInfo);

		// 본문 키워드 탐지
		if (body != null && Common.isNotEmpty(body.getText())) {
			keywordInfo.setBody(appendDetectedKeywords(keywordInfo.getBody(), checkKeyword(body.getText()), blockReasonKeywords));
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

			keywordInfo.setAttachName(appendDetectedKeywords(keywordInfo.getAttachName(), attachNameKeywords, blockReasonKeywords));
			keywordInfo.setAttach(appendDetectedKeywords(keywordInfo.getAttach(), attachTextKeywords, blockReasonKeywords));
		}

		// 전체 존재 여부
		keywordInfo.setExist(CollectionUtil.isNotEmpty(keywordInfo.getBody()) || CollectionUtil.isNotEmpty(keywordInfo.getAttachName()) || CollectionUtil.isNotEmpty(keywordInfo.getAttach()));

		int total = 0;
		// 병합된 keywords 생성
		if (keywordInfo.isExist()) {
			List<EmassDoc.KeywordInfo.Keyword> mergedList = mergeKeywords(keywordInfo.getBody(), keywordInfo.getAttachName(), keywordInfo.getAttach());
			total = sumCount(mergedList);
			keywordInfo.setKeywords(mergedList);
		}
		doc.setKeywordTotal(total);
		doc.setKeywordInfo(keywordInfo);
	}

	@SafeVarargs
	private List<EmassDoc.KeywordInfo.Keyword> mergeKeywords(List<EmassDoc.KeywordInfo.Keyword>... sources) {
		Map<String, EmassDoc.KeywordInfo.Keyword> merged = new LinkedHashMap<>();
		for (List<EmassDoc.KeywordInfo.Keyword> source : sources) {
			if (source == null) continue;
			for (EmassDoc.KeywordInfo.Keyword keyword : source) {
				if (keyword == null || Common.isEmpty(keyword.getName())) continue;
				String key = keyword.getName() + "\u0000" + keyword.isBlocked();
				EmassDoc.KeywordInfo.Keyword existing = merged.get(key);
				if (existing == null) {
					merged.put(key, EmassDoc.KeywordInfo.Keyword.builder()
							.name(keyword.getName())
							.count(keyword.getCount())
							.blocked(keyword.isBlocked())
							.build());
				} else {
					existing.setCount(existing.getCount() + keyword.getCount());
				}
			}
		}
		return merged.isEmpty() ? null : new ArrayList<>(merged.values());
	}

	private List<EmassDoc.KeywordInfo.Keyword> appendDetectedKeywords(List<EmassDoc.KeywordInfo.Keyword> current,
																	  List<EmassDoc.KeywordInfo.Keyword> detected,
																	  Set<String> blockReasonKeywords) {
		List<EmassDoc.KeywordInfo.Keyword> result = current == null ? new ArrayList<>() : new ArrayList<>(current);
		if (detected == null || detected.isEmpty()) return result.isEmpty() ? null : result;

		for (EmassDoc.KeywordInfo.Keyword keyword : detected) {
			if (keyword == null || Common.isEmpty(keyword.getName())) continue;
			if (blockReasonKeywords.contains(keyword.getName())) continue;
			addOrMerge(result, keyword.getName(), keyword.getCount(), false);
		}
		return result.isEmpty() ? null : result;
	}

	private void addOrMerge(List<EmassDoc.KeywordInfo.Keyword> keywords, String name, int count, boolean blocked) {
		for (EmassDoc.KeywordInfo.Keyword keyword : keywords) {
			if (keyword != null && Common.isEquals(keyword.getName(), name) && keyword.isBlocked() == blocked) {
				keyword.setCount(keyword.getCount() + count);
				return;
			}
		}
		keywords.add(EmassDoc.KeywordInfo.Keyword.builder().name(name).count(count).blocked(blocked).build());
	}

	private Set<String> collectBlockReasonKeywords(EmassDoc.KeywordInfo keywordInfo) {
		Set<String> result = new HashSet<>();
		collectBlockReasonKeywords(result, keywordInfo.getKeywords());
		collectBlockReasonKeywords(result, keywordInfo.getBody());
		collectBlockReasonKeywords(result, keywordInfo.getAttachName());
		collectBlockReasonKeywords(result, keywordInfo.getAttach());
		return result;
	}

	private void collectBlockReasonKeywords(Set<String> result, List<EmassDoc.KeywordInfo.Keyword> keywords) {
		if (keywords == null) return;
		for (EmassDoc.KeywordInfo.Keyword keyword : keywords) {
			if (keyword != null && keyword.isBlocked() && Common.isNotEmpty(keyword.getName())) {
				result.add(keyword.getName());
			}
		}
	}

	private int sumCount(List<EmassDoc.KeywordInfo.Keyword> keywords) {
		if (keywords == null) return 0;
		int total = 0;
		for (EmassDoc.KeywordInfo.Keyword keyword : keywords) {
			if (keyword != null) total += keyword.getCount();
		}
		return total;
	}

	private List<EmassDoc.KeywordInfo.Keyword> checkKeyword(final String keyword) {
		if (keyword == null) return null;

		List<EmassDoc.KeywordInfo.Keyword> result = new ArrayList<>();
		Map<String, Integer> keywords = keywordLoader.KEYWORD_MATCHER_REF.get().checkKeywordCounts(keyword);
		for (String key : keywords.keySet()) {
			result.add(EmassDoc.KeywordInfo.Keyword.builder().name(key).count(keywords.get(key)).blocked(false).build());
		}
		return result.isEmpty() ? null : result;
	}
}
