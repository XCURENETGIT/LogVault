package com.xcurenet.common.ahocorasick;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Aho-Corasick(0.6.3) 기반 키워드 탐지 유틸
 * - allowOverlaps(): 중첩 매치 허용 (예: "보안코드삭제" 내 "보안코드" 및 "보안코드삭제" 동시 탐지)
 * - 복합(AND) 키워드: "보안 사고" 처럼 공백 포함 키워드는 각 토큰 모두가 텍스트에 존재하면 충족
 */
@Log4j2
@Data
@ToString
public class KeywordMatcher implements Serializable {

	// === 설정 플래그 ===
	/**
	 * 대소문자 무시 (한글엔 영향 적고, 영문/숫자/기호 혼용 텍스트 고려 시 유용)
	 */
	private boolean ignoreCase = false;
	/**
	 * 공백 단위 단어만 매칭할지(권장: false). true면 "부분매치"를 방지하나 한글 결합어 탐지는 떨어짐
	 */
	private boolean onlyWholeWordsWhiteSpaceSeparated = false;
	/**
	 * 겹침 허용: 중첩 매치 필요 시 반드시 false
	 */
	private boolean ignoreOverlaps = false;

	// === 내부 구조 ===
	private final List<Keyword> keywordList = new ArrayList<>();

	/**
	 * 단일 키워드(공백 없음) 집합
	 */
	private final Set<String> normalKeywords = new HashSet<>();

	/**
	 * 복합(AND) 키워드 매핑
	 * key   : 원본 복합 키워드(예: "보안 사고")
	 * value : 구성 토큰 리스트(예: ["보안","사고"])
	 */
	private final Map<String, List<String>> complexKeywords = new HashMap<>();

	/**
	 * 각 토큰(단일 키워드 및 복합의 구성 토큰)을 담은 AC 트라이
	 */
	private transient Trie trie;
	private boolean prepared = false;

	// ========== 키워드 등록/준비 ==========

	/**
	 * 키워드 추가
	 *
	 * @param keywords 단일("보안") 또는 복합("보안 사고") 키워드
	 * @param minCount 최소 탐지 건수(단일/복합 모두에 적용)
	 */
	public void addKeyword(final String keywords, int minCount) {
		String trimmed = safeTrim(keywords);
		if (trimmed.isEmpty()) return;

		// 복합(AND) 키워드: 공백으로 분해
		String[] parts = trimmed.split("\\s+");
		if (parts.length > 1) {
			List<String> tokens = new ArrayList<>(parts.length);
			for (String p : parts) {
				String token = normalize(p);
				if (!token.isEmpty()) {
					tokens.add(token);
				}
			}
			if (!tokens.isEmpty()) {
				complexKeywords.put(trimmed, Collections.unmodifiableList(tokens));
			}
		} else {
			normalKeywords.add(trimmed);
		}

		keywordList.add(new Keyword(trimmed, minCount));
		prepared = false; // 새 키워드 추가 시 재준비 필요
	}

	/**
	 * AC 트라이 준비. 모든 단일 키워드 및 복합 키워드의 구성 토큰을 트라이에 반영.
	 */
	public void prepare() {
		Trie.TrieBuilder builder = Trie.builder();

		if (ignoreOverlaps) builder.ignoreOverlaps();
		if (onlyWholeWordsWhiteSpaceSeparated) builder.onlyWholeWordsWhiteSpaceSeparated();
		if (ignoreCase) builder.ignoreCase();

		// 1) 단일 키워드
		for (String k : normalKeywords) {
			String token = normalize(k);
			if (!token.isEmpty()) builder.addKeyword(token);
		}
		// 2) 복합(AND) 키워드의 구성 토큰
		for (List<String> tokens : complexKeywords.values()) {
			for (String token : tokens) {
				String norm = normalize(token);
				if (!norm.isEmpty()) builder.addKeyword(norm);
			}
		}

		trie = builder.build();
		prepared = true;
	}

	/**
	 * 신규: 키워드별 탐지 건수 반환 (String 입력)
	 */
	public Map<String, Integer> checkKeywordCounts(final String text) {
		if (text == null) return Collections.emptyMap();
		ensurePrepared();
		String query = normalize(text);
		Map<String, Integer> tokenCounts = emitTokenCounts(query);

		// 단일 키워드 카운트
		Map<String, Integer> result = new LinkedHashMap<>();
		for (String k : normalKeywords) {
			int cnt = tokenCounts.getOrDefault(normalize(k), 0);
			if (cnt > 0) result.put(k, cnt);
		}

		// 복합(AND) 키워드 충족 여부(모든 토큰 등장)
		for (Map.Entry<String, List<String>> e : complexKeywords.entrySet()) {
			String complexKey = e.getKey();
			List<String> tokens = e.getValue();

			boolean allHit = true;
			Integer minCountAcrossTokens = null;   // ★ 최소값으로 카운트
			for (String t : tokens) {
				int c = tokenCounts.getOrDefault(normalize(t), 0);
				if (c <= 0) {
					allHit = false;
					break;
				}
				minCountAcrossTokens = (minCountAcrossTokens == null) ? c : Math.min(minCountAcrossTokens, c);
			}
			if (allHit) {
				int countForComplex = (minCountAcrossTokens == null) ? 0 : minCountAcrossTokens;
				// ★ 복합 키워드 카운트 = 각 토큰 카운트의 최소값
				result.put(complexKey, countForComplex);
			}
		}

		log.debug("CHECK_KEYWORD_COUNTS | {}", result);
		return result;
	}

	/**
	 * 신규: 키워드별 탐지 건수 반환 (byte[] 입력)
	 */
	public Map<String, Integer> checkKeywordCounts(final byte[] text) {
		if (text == null || text.length == 0) return Collections.emptyMap();
		return checkKeywordCounts(new String(text, StandardCharsets.UTF_8));
	}

	/**
	 * 신규: minCount 기준으로 필터링된 카운트 반환
	 */
	public Map<String, Integer> checkKeywordOverMin(final String text) {
		Map<String, Integer> raw = checkKeywordCounts(text);
		if (raw.isEmpty()) return raw;

		Map<String, Integer> minMap = new HashMap<>();
		for (Keyword k : keywordList) {
			minMap.put(k.getKeyword(), k.getMinCount());
		}

		Map<String, Integer> filtered = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> e : raw.entrySet()) {
			int min = minMap.getOrDefault(e.getKey(), 1);
			if (e.getValue() >= min) {
				filtered.put(e.getKey(), e.getValue());
			}
		}
		return filtered;
	}

	// ========== 내부 유틸 ==========

	private void ensurePrepared() {
		if (!prepared || trie == null) prepare();
	}

	/**
	 * AC로부터 토큰(정규화된 키)별 카운트를 뽑는다.
	 */
	private Map<String, Integer> emitTokenCounts(String normalizedText) {
		Map<String, Integer> counts = new HashMap<>();
		Iterable<Emit> emits = trie.parseText(normalizedText);
		for (Emit e : emits) {
			String kw = e.getKeyword();
			counts.merge(kw, 1, Integer::sum);
		}
		return counts;
	}

	/**
	 * 입력 표준화: ignoreCase=true면 lower-case, 공백 정규화
	 */
	private String normalize(String s) {
		String x = safeTrim(s);
		if (ignoreCase) x = x.toLowerCase(Locale.ROOT);
		return x;
	}

	private String safeTrim(String s) {
		return s == null ? "" : s.trim();
	}

	// ========== DTO ==========

	@Data
	@AllArgsConstructor
	public static class Keyword {
		private String keyword;
		private int minCount;
	}

	// ========== 간단한 사용 예시 ==========
	public static void main(String[] args) {
		KeywordMatcher k = new KeywordMatcher();
		k.setIgnoreOverlaps(false);
		k.setIgnoreCase(true);
		// k.setOnlyWholeWordsWhiteSpaceSeparated(false);

		// 복합(AND) 키워드 등록
		k.addKeyword("감염기록", 4);

		k.prepare();

		String text = "감염기록";
		System.out.println("Counts    : " + k.checkKeywordCounts(text));
		System.out.println("Over-Min  : " + k.checkKeywordOverMin(text));
		// 기대: {"보안 사고"=2} (+ 단일 키워드 등록 시 "보안"=1, "사고"=1 도 함께 나옴)
	}
}
