package com.xcurenet.logvault.loader.type;

import com.xcurenet.common.ahocorasick.AhoCorasick;
import com.xcurenet.common.ahocorasick.SearchResult;
import lombok.Data;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.apache.ibatis.type.Alias;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;

@Log4j2
@Data
@Alias("KeywordInfo")
@ToString
@Component
public class KeywordInfo implements Serializable {
	private String keyword;

	private final AhoCorasick ahocorasick = new AhoCorasick();
	private final Set<String> normalKeywords = new HashSet<>();
	private final Map<String, List<String>> complexKeywords = new HashMap<>();
	private boolean prepared = false;

	public void addKeyword(final String keywords) {
		ahocorasick.add(keywords.toLowerCase().getBytes(), keywords);
	}

	public void prepare() {
		ahocorasick.prepare();
		prepared = true;
	}

	public Set<String> checkKeyword(final String text) {
		return checkKeyword(text.toLowerCase().getBytes());
	}

	public Set<String> checkKeyword(final byte[] text) {
		if (!prepared) {
			prepare();
		}
		final Set<String> results = new HashSet<>();
		if (text != null && text.length > 0) {
			@SuppressWarnings("rawtypes") final Iterator searcher = ahocorasick.search(text);
			while (searcher.hasNext()) {
				final SearchResult result = (SearchResult) searcher.next();
				final String keyword = (String) result.getOutputs().toArray()[0];
				log.debug("[CHECK_KEYWORD] add keyword : {}", keyword);
				results.add(keyword);
			}
		}
		log.debug("[CHECK_KEYWORD] total : {}", results);
		return results;
	}
}
