package com.xcurenet.common.ahocorasick;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterator returns a list of Search matches.
 */

class Searcher implements Iterator<SearchResult> {
	private SearchResult currentResult;
	private final AhoCorasick tree;

	Searcher(final AhoCorasick tree, final SearchResult result) {
		this.tree = tree;
		this.currentResult = result;
	}

	@Override
	public boolean hasNext() {
		return this.currentResult != null;
	}

	@Override
	public SearchResult next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		final SearchResult result = currentResult;
		currentResult = tree.continueSearch(currentResult);
		return result;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
}
