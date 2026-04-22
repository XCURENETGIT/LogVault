package com.xcurenet.logvault.loader;

import com.xcurenet.common.ahocorasick.KeywordMatcher;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.service.InfoLoaderService;
import com.xcurenet.logvault.loader.type.KeywordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Service
@RequiredArgsConstructor
public class KeywordLoader {
    private final InfoLoaderService infoLoaderService;
    public final AtomicReference<KeywordMatcher> KEYWORD_MATCHER_REF = new AtomicReference<>();

    /**
     * keyword name → UI_KEYWORD_CATEGORY SEQ
     */
    private final AtomicReference<Map<String, String>> KEYWORD_CATEGORY_SEQ_REF = new AtomicReference<>(Collections.emptyMap());

    public void load() {
        long version = infoLoaderService.getKeywordVersion();
        List<KeywordVO> keywords = infoLoaderService.getKeyword(version);
        KeywordMatcher keywordMatcher = new KeywordMatcher();
        Map<String, String> categorySeqMap = new ConcurrentHashMap<>();
        for (KeywordVO item : keywords) {
            log.debug("INFO_LOAD | Keyword: {}", item);
            if (Common.isEmpty(item.getKeywordNm()) || Common.isEquals(item.getUseYn(), "N")) continue;

            keywordMatcher.addKeyword(item.getKeywordNm());
            if (Common.isNotEmpty(item.getKeywordCategorySeq())) {
                categorySeqMap.put(item.getKeywordNm(), item.getKeywordCategorySeq());
            }
        }
        keywordMatcher.prepare();
        KEYWORD_MATCHER_REF.set(keywordMatcher);
        KEYWORD_CATEGORY_SEQ_REF.set(categorySeqMap);

        log.info("INFO_LOAD | Rule Version : {} | Keyword Size: {} | CategorySeqMap: {}", version, keywords.size(), categorySeqMap.size());
    }

    /**
     * keyword name으로 해당 키워드의 categorySeq를 반환한다.
     *
     * @param keywordName 키워드 이름
     * @return categorySeq (없으면 null)
     */
    public String getCategorySeq(String keywordName) {
        return KEYWORD_CATEGORY_SEQ_REF.get().get(keywordName);
    }
}
