package com.xcurenet.logvault.loader.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xcurenet.common.mybatis.JsonTypeContext;
import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.type.KeywordVO;
import com.xcurenet.logvault.loader.type.PatternInfo;
import com.xcurenet.logvault.loader.type.RuleContentWrapper;
import com.xcurenet.logvault.loader.type.ServiceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InfoLoaderService {
	private final InfoLoaderMapper mapper;

	public List<KeywordVO> getKeyword() {
		JsonTypeContext.set(new TypeReference<List<KeywordVO>>() {});

		RuleContentWrapper wrapper = mapper.getRuleHistory("UI_KEYWORD");
		if (wrapper == null) return Collections.emptyList();

		@SuppressWarnings("unchecked")
		List<KeywordVO> result = (List<KeywordVO>) wrapper.getRuleContent();
		return result;
	}

	public List<PatternInfo> getPatternInfo() {
		JsonTypeContext.set(new TypeReference<List<PatternInfo>>() {});

		RuleContentWrapper wrapper = mapper.getRuleHistory("UI_PATTERN");
		if (wrapper == null) return mapper.getPatternInfo(); // 최초 설치 후 Rule이 없다면...

		@SuppressWarnings("unchecked")
		List<PatternInfo> result = (List<PatternInfo>) wrapper.getRuleContent();
		return result;
	}

	public List<ServiceVO> getService() {
		JsonTypeContext.set(new TypeReference<List<ServiceVO>>() {});

		RuleContentWrapper wrapper = mapper.getRuleHistory("UI_SERVICE");
		if (wrapper == null) return mapper.getService(); // 최초 설치 후 Rule이 없다면...

		@SuppressWarnings("unchecked")
		List<ServiceVO> result = (List<ServiceVO>) wrapper.getRuleContent();
		return result;
	}
}
