package com.xcurenet.common.ahocorasick;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import lombok.extern.log4j.Log4j2;

/**
 * Quick and dirty code: measures the amount of time it takes to construct an
 * AhoCorasick tree out of all the words in <tt>/usr/share/dict/words</tt>.
 */
@Log4j2
public class TimeTrial {
	public static void main(final String[] args) throws IOException {
		final long startTime = System.currentTimeMillis();
		final AhoCorasick tree = new AhoCorasick();
		@SuppressWarnings("resource")
		final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("/usr/share/dict/words")));
		String line;
		while ((line = reader.readLine()) != null) {
			tree.add(line.getBytes(), null);
		}
		tree.prepare();
		final long endTime = System.currentTimeMillis();
		log.info("endTime - startTime = " + (endTime - startTime) + " milliseconds");
	}
}
