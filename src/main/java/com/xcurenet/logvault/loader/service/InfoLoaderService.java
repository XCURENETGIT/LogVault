package com.xcurenet.logvault.loader.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xcurenet.common.mybatis.JsonTypeContext;
import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.type.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class InfoLoaderService {
	private static final String UI_KEYWORD = "UI_KEYWORD";
	private static final String UI_PATTERN = "UI_PATTERN";
	private static final String UI_SERVICE = "UI_SERVICE";
	private static final String UI_ANOMALY_SCORE = "UI_ANOMALY_SCORE";
	private static final String UI_GUARD_RAIL = "UI_GUARD_RAIL";
	private static final String UI_SERVICE_COMPANY_ACCOUNT_MAPR = "UI_SERVICE_COMPANY_ACCOUNT_MAPR";

	private final InfoLoaderMapper mapper;

	public long getKeywordVersion() {
		return mapper.getLastVersion(UI_KEYWORD);
	}

	public long getPatternVersion() {
		return mapper.getLastVersion(UI_PATTERN);
	}

	public long getServiceVersion() {
		return mapper.getLastVersion(UI_SERVICE);
	}

	public long getAnomalyScoreVersion() {
		return mapper.getLastVersion(UI_ANOMALY_SCORE);
	}

	public long getGuardRailVersion() {
		return mapper.getLastVersion(UI_GUARD_RAIL);
	}

	public long getAccountVersion() {
		return mapper.getLastVersion(UI_SERVICE_COMPANY_ACCOUNT_MAPR);
	}


	public List<KeywordVO> getKeyword(long version) {
		JsonTypeContext.set(new TypeReference<List<KeywordVO>>() {});

		RuleContentWrapper wrapper = mapper.getRuleHistory(UI_KEYWORD, version);
		if (wrapper == null) return Collections.emptyList();

		@SuppressWarnings("unchecked")
		List<KeywordVO> result = (List<KeywordVO>) wrapper.getRuleContent();
		return result;
	}

	public List<PatternInfo> getPatternInfo(long version) {
		JsonTypeContext.set(new TypeReference<List<PatternInfo>>() {});

		RuleContentWrapper wrapper = mapper.getRuleHistory(UI_PATTERN, version);
		if (wrapper == null) return mapper.getPatternInfo(); // 최초 설치 후 Rule이 없다면...

		@SuppressWarnings("unchecked")
		List<PatternInfo> result = (List<PatternInfo>) wrapper.getRuleContent();
		return result;
	}

	public List<ServiceVO> getService(long version) {
		JsonTypeContext.set(new TypeReference<List<ServiceVO>>() {});

		RuleContentWrapper wrapper = mapper.getRuleHistory(UI_SERVICE, version);
		if (wrapper == null) return mapper.getService(); // 최초 설치 후 Rule이 없다면...

		@SuppressWarnings("unchecked")
		List<ServiceVO> result = (List<ServiceVO>) wrapper.getRuleContent();
		return result;
	}

	public List<AiServiceVO> getAiServices() {
		return mapper.getAiServices();
	}

	public List<ImageCategoryVO> getImageCategories() {
		return mapper.getImageCategories();
	}

	public List<AnomalyScoreVO> getAnomalyScore(long version) {
		JsonTypeContext.set(new TypeReference<List<AnomalyScoreVO>>() {});

		RuleContentWrapper wrapper = mapper.getRuleHistory(UI_ANOMALY_SCORE, version);
		if (wrapper == null) return Collections.emptyList();

		@SuppressWarnings("unchecked")
		List<AnomalyScoreVO> result = (List<AnomalyScoreVO>) wrapper.getRuleContent();
		return result;
	}

	public List<GuardRailVO> getGuardRail(long version) {
		JsonTypeContext.set(new TypeReference<List<GuardRailVO>>() {});

		RuleContentWrapper wrapper = mapper.getRuleHistory(UI_GUARD_RAIL, version);
		if (wrapper == null) return Collections.emptyList();

		@SuppressWarnings("unchecked")
		List<GuardRailVO> result = (List<GuardRailVO>) wrapper.getRuleContent();
		return result;
	}

	public List<AccountVO> getAccounts(long version) {
		JsonTypeContext.set(new TypeReference<List<AccountVO>>() {});

		RuleContentWrapper wrapper = mapper.getRuleHistory(UI_SERVICE_COMPANY_ACCOUNT_MAPR, version);
		if (wrapper == null) return Collections.emptyList();

		@SuppressWarnings("unchecked")
		List<AccountVO> result = (List<AccountVO>) wrapper.getRuleContent();
		return result;
	}




}
