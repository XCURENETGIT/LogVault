package com.xcurenet.common.ahocorasick;

import com.xcurenet.common.utils.DateUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.util.StopWatch;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Aho-Corasick 기반 키워드 탐지 유틸
 * - AND  : 구성 토큰 모두 존재
 * - EXACT: 문자열 완전 일치(contains)
 */
@Log4j2
@Data
@ToString
public class KeywordMatcher implements Serializable {

	public enum MatchType {
		AND, EXACT
	}

	private boolean ignoreCase = false;
	private boolean ignoreOverlaps = false;
	private boolean onlyWholeWordsWhiteSpaceSeparated = false;

	/**
	 * 등록된 키워드 전체
	 */
	private final List<Keyword> keywords = new ArrayList<>();

	/**
	 * AND 키워드용 토큰 집합
	 */
	private final Set<String> andTokens = new HashSet<>();

	private transient Trie trie;
	private boolean prepared = false;

	/**
	 * 키워드 등록
	 *
	 * @param keyword  키워드
	 */
	public void addKeyword(String keyword) {
		addKeyword(keyword, MatchType.EXACT);
	}

	/**
	 * 키워드 등록
	 *
	 * @param keyword  키워드
	 * @param type     탐지 방식 (EXACT:완전 일치, AND 텍스트 내 구성 토큰 모두 존재)
	 */
	public void addKeyword(String keyword, MatchType type) {
		String trimmed = safeTrim(keyword);
		if (trimmed.isEmpty()) return;

		Keyword k = new Keyword(trimmed, type);
		keywords.add(k);
		if (type == MatchType.AND) {
			for (String part : trimmed.split("\\s+")) {
				String token = normalize(part);
				if (!token.isEmpty()) {
					andTokens.add(token);
				}
			}
		}
		prepared = false;
	}

	public void prepare() {
		Trie.TrieBuilder builder = Trie.builder();
		if (ignoreOverlaps) builder.ignoreOverlaps();
		if (ignoreCase) builder.ignoreCase();
		if (onlyWholeWordsWhiteSpaceSeparated) builder.onlyWholeWordsWhiteSpaceSeparated();

		for (String token : andTokens) {
			builder.addKeyword(token);
		}
		trie = builder.build();
		prepared = true;
	}

	private void ensurePrepared() {
		if (!prepared || trie == null) prepare();
	}

	/**
	 * 키워드 탐지
	 *
	 * @param text 탐지할 텍스트
	 * @return 탐지 결과
	 */
	public Map<String, Integer> checkKeywordCounts(String text) {
		if (text == null) return Collections.emptyMap();
		ensurePrepared();

		String normalizedText = normalize(text);
		Map<String, Integer> tokenCounts = emitTokenCounts(normalizedText);

		Map<String, Integer> result = new LinkedHashMap<>();
		for (Keyword k : keywords) {
			switch (k.matchType) {

				case EXACT -> {
					String target = normalize(k.keyword);
                    int count = countExact(normalizedText, target);
                    if (count > 0) {
                        result.put(k.keyword, count);
                    }
				}

				case AND -> {
					String[] parts = k.keyword.split("\\s+");
					Integer minAcross = null;
					boolean allHit = true;

					for (String p : parts) {
						int c = tokenCounts.getOrDefault(normalize(p), 0);
						if (c <= 0) {
							allHit = false;
							break;
						}
						minAcross = (minAcross == null) ? c : Math.min(minAcross, c);
					}

					if (allHit && minAcross != null) {
						result.put(k.keyword, minAcross);
					}
				}
			}
		}

		log.debug("KEYWORD_MATCH_RESULT | {}", result);
		return result;
	}

	public Map<String, Integer> checkKeywordCounts(byte[] text) {
		if (text == null || text.length == 0) return Collections.emptyMap();
		return checkKeywordCounts(new String(text, StandardCharsets.UTF_8));
	}

	private Map<String, Integer> emitTokenCounts(String text) {
		Map<String, Integer> counts = new HashMap<>();
		Iterable<Emit> emits = trie.parseText(text);
		for (Emit e : emits) {
			counts.merge(e.getKeyword(), 1, Integer::sum);
		}
		return counts;
	}

	private int countExact(String text, String target) {
		int count = 0;
		int idx = 0;
		while ((idx = text.indexOf(target, idx)) >= 0) {
			count++;
			idx += target.length();
		}
		return count;
	}

	private String normalize(String s) {
		String x = safeTrim(s);
		if (ignoreCase) x = x.toLowerCase(Locale.ROOT);
		return x;
	}

	private String safeTrim(String s) {
		return s == null ? "" : s.trim();
	}

	@Data
	@AllArgsConstructor
	public static class Keyword {
		private String keyword;
		private MatchType matchType;
	}

	public static void main(String[] args) {
		StopWatch sw = DateUtils.start();
		KeywordMatcher km = new KeywordMatcher();
		km.setIgnoreCase(true);

		// EXACT
		km.addKeyword("버그", MatchType.EXACT);
		// AND
		km.addKeyword("장애 버그", MatchType.AND);

		km.addKeyword("요청건 예정", MatchType.AND);

		km.addKeyword("개발 요청서", MatchType.EXACT);
		km.addKeyword("개발요청서", MatchType.EXACT);

		km.prepare();

		String text = """
				연구소 이슈 관리																												
				항목 ID	완료 예정일	항목	고객사 / 담당 엔지니어	분류	중요도	제품명	관련 링크	할당	연구소 상태	진척도	연구소 담당팀	완료일	업무 시작일															
				연구소"	"waiting for
				[10/01] 개발 요청서 작성하여 공유하겠다는 메일 회신
				""";

		System.out.println(km.checkKeywordCounts(text) + " | " + DateUtils.stop(sw));
	}
}
