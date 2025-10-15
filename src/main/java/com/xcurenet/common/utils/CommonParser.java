package com.xcurenet.common.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

public class CommonParser {
	public static final String BODY_TEXT = "BODY_TEXT";
	public static final String FROM = "FROM";
	public static final String TO = "TO";
	public static final String IM_DELIMITER = 'ÿ' + new String(new byte[]{-1});
	public static final String _ID_ = "[ID]";
	public static final String _FROM_ = "[FROM]";
	public static final String _TO_ = "[TO]";

	public CommonParser() {
	}

	public static void parseIMFile(Map<String, Object> map, InputStream is, String charset) throws IOException {
		BufferedReader reader = null;
		try {
			StringBuilder sb = new StringBuilder();
			Set<String> to = new HashSet<>();
			reader = new BufferedReader(new InputStreamReader(is, charset));
			String line;
			String from = null;
			while ((line = reader.readLine()) != null) {
				line = StringUtils.stripEnd(line, IM_DELIMITER);
				sb.append(line).append('\n');
				if (StringUtils.isEmpty(from)) {
					from = StringUtils.defaultIfEmpty(parseIMField(line, "[FROM]"), parseIMField(line, "[ID]"));
				}
				String toValue = parseIMField(line, "[TO]");
				if (StringUtils.isNotEmpty(toValue)) {
					String[] split = toValue.split(",");
					Collections.addAll(to, split);
				}
			}

			if (StringUtils.isNotEmpty(from)) {
				map.put("FROM", from);
			}

			if (!to.isEmpty()) {
				map.put("TO", to);
			}

			if (!sb.isEmpty()) {
				map.put("BODY_TEXT", sb.toString());
			}
		} finally {
			IOUtils.closeQuietly(reader);
		}
	}

	public static String parseIMField(String line, String key) {
		int pos = 0;
		String ret = null;
		if ((pos = line.indexOf(key, pos)) >= 0) {
			int spos = pos + key.length();
			int epos = line.indexOf(91, spos);
			String value = (epos == -1 ? line.substring(spos) : line.substring(spos, epos)).trim();
			if (StringUtils.isNotEmpty(value)) {
				ret = value;
			}
		}

		return ret;
	}

	public static void parseBodyFile(Map<String, Object> map, InputStream is, String charset) throws IOException {
		BufferedReader reader = null;

		try {
			reader = new BufferedReader(new InputStreamReader(is, charset));
			String line;
			StringBuilder sb = new StringBuilder();
			while ((line = reader.readLine()) != null) {
				sb.append(line).append('\n');
			}

			if (!sb.isEmpty()) {
				map.put("BODY_TEXT", sb.toString());
			}
		} finally {
			IOUtils.closeQuietly(reader);
		}

	}

	public static void parseCNTBodyFile(Map<String, Object> map, InputStream is, String charset) throws IOException {
		BufferedReader reader = null;

		try {
			reader = new BufferedReader(new InputStreamReader(is, charset));
			String line;
			StringBuilder sb = new StringBuilder();

			while ((line = reader.readLine()) != null) {
				String[] split = line.split(":", 2);
				if (split[0].trim().equalsIgnoreCase("CONNECT_TIME") || split[0].trim().equalsIgnoreCase("DISCONNECT_TIME")) {
					sb.append(line).append('\n');
				}

				if (!sb.isEmpty()) {
					map.put("BODY_TEXT", sb.toString());
				}
			}
		} finally {
			IOUtils.closeQuietly(reader);
		}

	}
}
