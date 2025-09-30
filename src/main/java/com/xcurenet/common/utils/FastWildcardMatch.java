package com.xcurenet.common.utils;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;

import java.util.*;
import java.util.Map.Entry;

public class FastWildcardMatch {
	private final Set<String> plains = new HashSet<>();
	private final NavigableMap<String, List<String[]>> mapGlobs = new TreeMap<>();
	private final NavigableMap<String, List<String[]>> mapGlobsReverse = new TreeMap<>();
	private final List<String[]> wildGlobs = new ArrayList<>();
	private final List<String[]> wildGlobsReverse = new ArrayList<>();

	private final Map<String, Boolean> cache = new HashMap<>();

	private static final IOCase SENSITIVE = IOCase.SENSITIVE;
	private final boolean caseSensitivity;

	public FastWildcardMatch(final boolean caseSensitivity) {
		this.caseSensitivity = caseSensitivity;
	}

	private static String reverseString(final String text) {
		return new StringBuilder(text).reverse().toString();
	}

	public void addPattern(final String pattern) {
		final String tmpPattern = caseSensitivity ? pattern : pattern.toLowerCase();
		final boolean hasMultiple = tmpPattern.indexOf('*') != -1;
		addPattern(tmpPattern, hasMultiple, false);
	}

	public Set<String> getPattern() {
		return plains;
	}

	private void addPattern(final String pattern, final boolean hasMultiple, final boolean reverse) {
		if (!reverse) {
			plains.add(pattern);
			if (hasMultiple) {
				plains.add(pattern.replaceAll("\\*", ""));
			}

			// 와일드카드로 시작하면 Reverse 로 등록하자! 끝글자 보고 영양가 없으면 패스
			// ? 가 * 보다 그나마 앞에 오는게 낫다
			final char first = pattern.charAt(0);
			final char last = pattern.charAt(pattern.length() - 1);
			if ((first == '*' && last != '*') || (first == '?' && last != '*' && last != '?')) {
				addPattern(reverseString(pattern), hasMultiple, true);
				return;
			}
		}

		if (hasMultiple || pattern.indexOf('?') != -1) {
			final String[] wcs = splitOnTokens(pattern);
			final String first = wcs[0];
			if ("*".equals(first) || "?".equals(first)) {
				final List<String[]> globs = reverse ? wildGlobsReverse : wildGlobs;
				globs.add(wcs);
			} else {
				final NavigableMap<String, List<String[]>> map = reverse ? mapGlobsReverse : mapGlobs;
				List<String[]> globs = map.computeIfAbsent(first, k -> new ArrayList<>());
				globs.add(wcs);
			}
		}
	}

	public void reset() {
		plains.clear();
		mapGlobs.clear();
		mapGlobsReverse.clear();
		wildGlobs.clear();
		wildGlobsReverse.clear();
	}

	public boolean isMatch(final String text) {
		final String tmpText = caseSensitivity ? text : text.toLowerCase();

		if (cache.containsKey(tmpText)) {
			return cache.get(tmpText);
		}

		final boolean result = isMatchNoCache(tmpText);
		cache.put(tmpText, result);
		return result;
	}

	private boolean isMatchNoCache(final String text) {
		if (!plains.isEmpty() && plains.contains(text)) {
			return true;
		}

		if (wildcardMatch(text, mapGlobs)) {
			return true;
		}

		final String reverseText = reverseString(text);
		if (wildcardMatch(reverseText, mapGlobsReverse)) {
			return true;
		}

		if (wildcardMatch(text, wildGlobs)) {
			return true;
		}

		if (wildcardMatch(reverseText, wildGlobsReverse)) {
			return true;
		}

		return false;
	}

	private boolean wildcardMatch(final String text, final NavigableMap<String, List<String[]>> map) {
		if (!map.isEmpty()) {
			Entry<String, List<String[]>> entry = map.floorEntry(text);
			while (entry != null) {
				if (text.startsWith(entry.getKey())) {
					if (wildcardMatch(text, entry.getValue())) {
						return true;
					}
				}
				entry = map.lowerEntry(entry.getKey());
			}
		}
		return false;
	}

	private boolean wildcardMatch(final String text, final List<String[]> globs) {
		for (final String[] wcs : globs) {
			if (wildcardMatch(text, wcs)) {
				return true;
			}
		}
		return false;
	}

	private static String[] splitOnTokens(final String pattern) {
		if (pattern.indexOf('?') == -1 && pattern.indexOf('*') == -1) {
			return new String[]{pattern};
		}

		final char[] array = pattern.toCharArray();
		final ArrayList<String> list = new ArrayList<String>();
		final StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < array.length; i++) {
			if (array[i] == '?' || array[i] == '*') {
				if (!buffer.isEmpty()) {
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
		if (!buffer.isEmpty()) {
			list.add(buffer.toString());
		}

		return list.toArray(new String[0]);
	}

	private static boolean wildcardMatch(final String text, final String[] wcs) {
		boolean anyChars = false;
		int textIdx = 0;
		int wcsIdx = 0;
		final Stack<int[]> backtrack = new Stack<>();

		// loop around a backtrack stack, to handle complex * matching
		do {
			if (!backtrack.isEmpty()) {
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
							backtrack.push(new int[]{wcsIdx, repeat});
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

		} while (!backtrack.isEmpty());

		return false;
	}

	public static void test() {
		final String[] patterns = new String[]{"jhrhee", "sjlee", "ysryu", "powerjoh", "garnhm", "minsun", "gprock", "startx*", "*123", "st?rtx", "?tartx", "jckang", "vincent", "kihyun93", "hth0817", "yhlee", "hershes", "chilgogli"};
		final String[] inputs = new String[]{"jhrhee", "chilgogli", "startx", "startx9999", "xxx123", "notfound", "a", "leo", "chobg", "cobit", "usang81", "shinmk", "stardom1230", "hustler"};

		final int n = 1000000;

		boolean find = false;
		long millis = System.currentTimeMillis();
		for (final String input : inputs) {
			for (int i = 0; i < n; i++) {
				for (final String pattern : patterns) {
					find = FilenameUtils.wildcardMatch(input, pattern);
					if (find) {
						break;
					}
				}
			}
			System.out.format("isMatch(%s): %s\n", input, find);
		}
		System.out.println("FilenameUtils: " + (System.currentTimeMillis() - millis) + " ms");

		final FastWildcardMatch match = new FastWildcardMatch(true);
		for (final String pattern : patterns) {
			match.addPattern(pattern);
		}

		find = false;
		millis = System.currentTimeMillis();
		for (final String input : inputs) {
			for (int i = 0; i < n; i++) {
				find = match.isMatch(input);
			}
			System.out.format("isMatch(%s): %s\n", input, find);
		}
		System.out.println("FastWildcardMatch: " + (System.currentTimeMillis() - millis) + " ms");
	}

	public static void testReverse() {
		final String[] patterns = new String[]{"*.xcurenet.com", "*.naver.com", "*.daum.net", "*.hanmail.net", "*.nate.com", "*.yahoo.co.kr", "*.clien.net", "*.ppomppu.co.kr", "*.cloudera.com", "*.apache.org"};
		final List<String> list = new ArrayList<>(Arrays.asList(patterns));
		for (int i = 0; i < 1000; i++) {
			String last = switch (i % 5) {
				case 0 -> "com";
				case 1 -> "net";
				case 2 -> "co.kr";
				case 3 -> "go.kr";
				case 4 -> "org";
				default -> "";
			};
			list.add(String.format("*.%d.%s", i, last));
		}

		final String input = "www.notfound.com";
		final int n = 1000000;
		final FastWildcardMatch match = new FastWildcardMatch(false);
		for (final String pattern : list) {
			match.addPattern(pattern);
		}

		boolean find = false;
		final long millis = System.currentTimeMillis();
		for (int i = 0; i < n; i++) {
			find = match.isMatch(input);
		}
		System.out.format("isMatch(%s): %s\n", input, find);
		System.out.println("FastWildcardMatch: " + (System.currentTimeMillis() - millis) + " ms");
	}
}
