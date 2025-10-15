package com.xcurenet.common.regex;

import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
public class PatternDetector {

	private final Map<String, DetectOptions> patternMap;

	public PatternDetector(Map<String, DetectOptions> compiled) {
		this.patternMap = Collections.unmodifiableMap(new LinkedHashMap<>(compiled));
	}

	/**
	 * 전체 키에 대해 탐지 실행
	 * - 모든 매치를 수집(limit=0)
	 * - 수집된 개수가 minCount(임계치) 이상일 때만 out에 포함
	 */
	public Map<String, List<MatchResult>> detectAll(String text) {
		Objects.requireNonNull(text, "text");

		Map<String, List<MatchResult>> out = new LinkedHashMap<>();
		for (Map.Entry<String, DetectOptions> e : patternMap.entrySet()) {
			String key = e.getKey();
			DetectOptions opt = e.getValue();
			Pattern p = opt.getCompile();

			// ✅ 전체 탐지: limit=0 (무제한)
			List<MatchResult> matches = findAll(text, p, 999);

			// ✅ 임계치(minCount) 적용: 미만이면 제외
			int minCount = Math.max(0, opt.getMinCount());
			if (!matches.isEmpty() && matches.size() >= minCount) {
				out.put(key, matches);
			}
		}
		return out;
	}

	public static List<MatchResult> findAll(String text, Pattern pattern, int max) {
		List<MatchResult> list = new ArrayList<>();
		Matcher m = pattern.matcher(text);
		while (m.find()) {
			list.add(new MatchResult(m.start(), m.end(), m.group(), Collections.emptyMap()));
			if (max > 0 && list.size() >= max) break;
		}
		return list;
	}

	/**
	 * 번호 기반 그룹만 추출 (명명 그룹 사용 시 필요에 맞게 확장)
	 */
	private static Map<String, String> extractGroups(Matcher m) {
		int gc = m.groupCount();
		if (gc <= 0) return Collections.emptyMap();
		Map<String, String> g = new LinkedHashMap<>(gc);
		for (int i = 1; i <= gc; i++) {
			g.put("$" + i, m.group(i));
		}
		return g;
	}
}
