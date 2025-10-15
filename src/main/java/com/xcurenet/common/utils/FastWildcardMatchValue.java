package com.xcurenet.common.utils;

import org.apache.commons.io.IOCase;

import java.util.*;
import java.util.Map.Entry;

public class FastWildcardMatchValue<T> {
	private final Map<String, T> plainPatterns = new HashMap<>();
	private final NavigableMap<String, List<Pattern>> mapPatterns = new TreeMap<>();
	private final NavigableMap<String, List<Pattern>> mapPatternsReverse = new TreeMap<>();
	private final List<Pattern> wildPatterns = new ArrayList<>();
	private final List<Pattern> wildPatternsReverse = new ArrayList<>();

	private final Map<String, T> cache = new HashMap<>();

	private final static IOCase SENSITIVE = IOCase.SENSITIVE;
	private final boolean caseSensitivity;

	private class Pattern {
		public String[] wcs;
		public T value;

		public Pattern(final String[] wcs, final T value) {
			this.wcs = wcs;
			this.value = value;
		}
	}

	public FastWildcardMatchValue(final boolean caseSensitivity) {
		this.caseSensitivity = caseSensitivity;
	}

	private static String reverseString(final String text) {
		return new StringBuilder(text).reverse().toString();
	}

	public void addPattern(final String pattern, final T value) {
		final String tmpPattern = caseSensitivity ? pattern : pattern.toLowerCase();
		final boolean hasMultiple = tmpPattern.indexOf('*') != -1;
		addPattern(tmpPattern, value, hasMultiple, false);
	}

	private void addPattern(final String pattern, final T value, final boolean hasMultiple, final boolean reverse) {
		if (!reverse) {
			plainPatterns.put(pattern, value);
			if (hasMultiple) {
				plainPatterns.put(pattern.replaceAll("\\*", ""), value);
			}

			// 와일드카드로 시작하면 Reverse 로 등록하자! 끝글자 보고 영양가 없으면 패스
			// ? 가 * 보다 그나마 앞에 오는게 낫다
			final char first = pattern.charAt(0);
			final char last = pattern.charAt(pattern.length() - 1);
			if ((first == '*' && last != '*') || (first == '?' && last != '*' && last != '?')) {
				addPattern(reverseString(pattern), value, hasMultiple, true);
				return;
			}
		}

		if (hasMultiple || pattern.indexOf('?') != -1) {
			final String[] wcs = splitOnTokens(pattern);
			final String first = wcs[0];
			if ("*".equals(first) || "?".equals(first)) {
				final List<Pattern> patterns = reverse ? wildPatternsReverse : wildPatterns;
				patterns.add(new Pattern(wcs, value));
			} else {
				final NavigableMap<String, List<Pattern>> map = reverse ? mapPatternsReverse : mapPatterns;
				List<Pattern> patterns = map.get(first);
				if (patterns == null) {
					patterns = new ArrayList<Pattern>();
					map.put(first, patterns);
				}
				patterns.add(new Pattern(wcs, value));
			}
		}
	}

	private static String[] splitOnTokens(final String pattern) {
		if (pattern.indexOf('?') == -1 && pattern.indexOf('*') == -1) {
			return new String[] { pattern };
		}

		final char[] array = pattern.toCharArray();
		final ArrayList<String> list = new ArrayList<String>();
		final StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < array.length; i++) {
			if (array[i] == '?' || array[i] == '*') {
				if (buffer.length() != 0) {
					list.add(buffer.toString());
					buffer.setLength(0);
				}
				if (array[i] == '?') {
					list.add("?");
				} else if (list.isEmpty() || (i > 0 && !list.get(list.size() - 1).equals("*"))) {
					list.add("*");
				}
			} else {
				buffer.append(array[i]);
			}
		}
		if (buffer.length() != 0) {
			list.add(buffer.toString());
		}

		return list.toArray(new String[0]);
	}

	public void reset() {
		plainPatterns.clear();
		mapPatterns.clear();
		mapPatternsReverse.clear();
		wildPatterns.clear();
		wildPatternsReverse.clear();
	}

	public interface MatchInterface<T> {
		boolean match(T value);
	}

	private class DefaultMatch implements MatchInterface<T> {
		@Override
		public boolean match(final T value) {
			return true;
		}
	}

	private final DefaultMatch defaultMatch = new DefaultMatch();

	public T match(final String text) {
		return match(text, defaultMatch);
	}

	public T match(final String text, final MatchInterface<T> matchInterface) {
		final String tmpText = caseSensitivity ? text : text.toLowerCase();
		if (cache.containsKey(tmpText)) {
			final T result = cache.get(tmpText);
			if (defaultMatch.equals(matchInterface)) {
				// 디폴트일 경우엔 그냥 캐싱 결과 리턴
				return result;
			}

			if (result != null && matchInterface.match(result)) {
				return result;
			}
		}

		final T result = matchNoCache(tmpText, matchInterface);
		cache.put(tmpText, result);
		return result;
	}

	private T matchNoCache(final String text, final MatchInterface<T> matchInterface) {
		T value = plainPatterns.get(text);
		if (value != null && matchInterface.match(value)) {
			return value;
		}

		value = wildcardMatch(text, mapPatterns);
		if (value != null && matchInterface.match(value)) {
			return value;
		}

		final String reverseText = reverseString(text);
		value = wildcardMatch(reverseText, mapPatternsReverse);
		if (value != null && matchInterface.match(value)) {
			return value;
		}

		value = wildcardMatch(text, wildPatterns);
		if (value != null && matchInterface.match(value)) {
			return value;
		}

		value = wildcardMatch(reverseText, wildPatternsReverse);
		if (value != null && matchInterface.match(value)) {
			return value;
		}

		return null;
	}

	private T wildcardMatch(final String text, final NavigableMap<String, List<Pattern>> map) {
		if (!map.isEmpty()) {
			Entry<String, List<Pattern>> entry = map.floorEntry(text);
			while (entry != null) {
				if (text.startsWith(entry.getKey())) {
					final T value = wildcardMatch(text, entry.getValue());
					if (value != null) {
						return value;
					}
				}
				entry = map.lowerEntry(entry.getKey());
			}
		}
		return null;
	}

	private T wildcardMatch(final String text, final List<Pattern> patterns) {
		if (!patterns.isEmpty()) {
			for (final Pattern pattern : patterns) {
				if (wildcardMatch(text, pattern.wcs)) {
					return pattern.value;
				}
			}
		}
		return null;
	}

	private static boolean wildcardMatch(final String text, final String[] wcs) {
		boolean anyChars = false;
		int textIdx = 0;
		int wcsIdx = 0;
		final Stack<int[]> backtrack = new Stack<int[]>();

		// loop around a backtrack stack, to handle complex * matching
		do {
			if (backtrack.size() > 0) {
				final int[] array = backtrack.pop();
				wcsIdx = array[0];
				textIdx = array[1];
				anyChars = true;
			}

			// loop whilst tokens and text left to process
			while (wcsIdx < wcs.length) {
				if (wcs[wcsIdx].equals("?")) {
					// ? so move to next text char
					textIdx++;
					if (textIdx > text.length()) {
						break;
					}
					anyChars = false;
				} else if (wcs[wcsIdx].equals("*")) {
					// set any chars status
					anyChars = true;
					if (wcsIdx == wcs.length - 1) {
						textIdx = text.length();
					}
				} else {
					// matching text token
					if (anyChars) {
						// any chars then try to locate text token
						textIdx = SENSITIVE.checkIndexOf(text, textIdx, wcs[wcsIdx]);
						if (textIdx == -1) {
							// token not found
							break;
						}
						final int repeat = SENSITIVE.checkIndexOf(text, textIdx + 1, wcs[wcsIdx]);
						if (repeat >= 0) {
							backtrack.push(new int[] { wcsIdx, repeat });
						}
					} else {
						// matching from current position
						if (!SENSITIVE.checkRegionMatches(text, textIdx, wcs[wcsIdx])) {
							// couldnt match token
							break;
						}
					}

					// matched text token, move text index to end of matched token
					textIdx += wcs[wcsIdx].length();
					anyChars = false;
				}

				wcsIdx++;
			}

			// full match
			if (wcsIdx == wcs.length && textIdx == text.length()) {
				return true;
			}

		} while (backtrack.size() > 0);

		return false;
	}
}
