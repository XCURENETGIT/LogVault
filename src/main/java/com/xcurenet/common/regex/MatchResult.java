package com.xcurenet.common.regex;

import java.util.Map;

public record MatchResult(int start, int end, String match, Map<String, String> groups) {
	public MatchResult(int start, int end, String match, Map<String, String> groups) {
		this.start = start;
		this.end = end;
		this.match = match;
		this.groups = groups == null ? Map.of() : Map.copyOf(groups);
	}
}
