package com.xcurenet.logvault.loader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xcurenet.common.ahocorasick.KeywordMatcher;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.service.InfoLoaderService;
import com.xcurenet.logvault.loader.type.KeywordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Service
@RequiredArgsConstructor
public class KeywordLoader {
	private final InfoLoaderService infoLoaderService;
	public final AtomicReference<KeywordMatcher> KEYWORD_MATCHER_REF = new AtomicReference<>();
	public final AtomicReference<Set<String>> KEYWORD_ALARM_REF = new AtomicReference<>();
	public final AtomicReference<Set<String>> KEYWORD_SYSLOG_REF = new AtomicReference<>();

	public void load() {
		long version = infoLoaderService.getKeywordVersion();
		List<KeywordVO> keywords = infoLoaderService.getKeyword(version);
		KeywordMatcher keywordMatcher = new KeywordMatcher();
		Set<String> alarmSet = new HashSet<>();
		Set<String> syslogSet = new HashSet<>();
		for (KeywordVO item : keywords) {
			log.debug("INFO_LOAD | Keyword: {}", item);
			if (Common.isEmpty(item.getKeywordNm()) || Common.isEquals(item.getUseYn(), "N")) continue;

			keywordMatcher.addKeyword(item.getKeywordNm(), item.getMinCnt());
			if (Common.isEquals(item.getAlarmYn(), "Y")) {
				alarmSet.add(item.getKeywordNm());
			}
			if (Common.isEquals(item.getSyslogYn(), "Y")) {
				syslogSet.add(item.getKeywordNm());
			}
		}
		keywordMatcher.prepare();
		KEYWORD_MATCHER_REF.set(keywordMatcher);
		KEYWORD_ALARM_REF.set(alarmSet);
		KEYWORD_SYSLOG_REF.set(syslogSet);

		log.info("INFO_LOAD | Rule Version : {} | Keyword Size: {}", version, keywords.size());
	}

	public Set<String> getKeywordAlert() {
		return KEYWORD_ALARM_REF.get();
	}

	public Set<String> getKeywordSyslog() {
		return KEYWORD_SYSLOG_REF.get();
	}
}
