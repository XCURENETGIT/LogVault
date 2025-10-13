package com.xcurenet.logvault.loader.type;

import lombok.ToString;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

@ToString
@Component
public class KeywordData {

	private final KeywordInfo info;

	public KeywordData(KeywordInfo info) {
		this.info = info;
	}

	public Set<String> checkKeyword(final String text) {
		if (info == null) return Collections.emptySet();
		return info.checkKeyword(text);
	}
}
