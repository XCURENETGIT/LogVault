package com.xcurenet.logvault.loader;

import com.xcurenet.common.ahocorasick.KeywordMatcher;
import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.type.KeywordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Service
@RequiredArgsConstructor
public class KeywordLoader {
	private final InfoLoaderMapper mapper;
	public final AtomicReference<KeywordMatcher> KEYWORD_MATCHER_REF = new AtomicReference<>();

	public void load() {
		List<KeywordVO> keywords = mapper.getKeyword();
		KeywordMatcher keywordMatcher = new KeywordMatcher();
		for (KeywordVO item : keywords) {
			if (CommonUtil.isEmpty(item.getKeywordNm())) continue;
			keywordMatcher.addKeyword(item.getKeywordNm(), item.getMinCnt());
		}
		keywordMatcher.prepare();
		KEYWORD_MATCHER_REF.set(keywordMatcher);
		log.info("[INFO_LOAD] Keyword Size: {}", keywords.size());
	}
}
