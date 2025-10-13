package com.xcurenet.common.utils;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import ua_parser.Client;
import ua_parser.Parser;

import java.util.*;

@Log4j2
public class HttpHeaderUtil {
	private static final Parser AGENT_PARSER = new Parser();
	private static final int MAX_CACHE_SIZE = 100_000;
	// LRU Cache: 가장 오래된 항목 자동 제거
	private static final Map<String, Client> UA_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Client> eldest) {
			return size() > MAX_CACHE_SIZE;
		}
	});

	public static HttpHeader parserHeader(final String raw) {
		if (raw == null) return null;

		HttpHeader header = null;
		try {
			List<String> lines = IOUtils.readLines(raw);

			int splitIndex = lines.indexOf("");
			List<String> requestLines = lines.subList(0, splitIndex);
			List<String> responseLines = lines.subList(splitIndex + 1, lines.size());

			HttpHeader.HttpRequestHeader request = parseRequest(requestLines);
			HttpHeader.HttpResponseHeader response = parseResponse(responseLines);

			header = new HttpHeader();
			header.setRequestHeader(request);
			header.setResponseHeader(response);
			header.setAgentString(request.getHeaders().get("user-agent"));
			header.setClient(parse(request.getHeaders().get("user-agent")));

			logHeader("Request", request.getMethod(), request.getUrl(), request.getProtocol(), request.getHeaders());
			logHeader("Response", response.getProtocol(), response.getStatus(), null, response.getHeaders());
		} catch (Exception e) {
			log.warn("HTTP Header 파싱 실패", e);
		}
		return header;
	}

	public static Client parse(final String userAgent) {
		Client client = UA_CACHE.get(userAgent);
		if (client == null) {
			client = AGENT_PARSER.parse(userAgent);
			UA_CACHE.put(userAgent, client);
		}
		return client;
	}

	private static HttpHeader.HttpRequestHeader parseRequest(List<String> lines) {
		HttpHeader.HttpRequestHeader header = new HttpHeader.HttpRequestHeader();
		Map<String, String> map = new LinkedHashMap<>();

		if (!lines.isEmpty()) {
			String[] parts = lines.get(0).split(" ");
			if (parts.length >= 3) {
				header.setMethod(parts[0]);
				header.setUrl(parts[1]);
				header.setProtocol(parts[2]);
			}
			for (int i = 1; i < lines.size(); i++) {
				Map<String, String> h = parseHeaderLine(lines.get(i));
				if (h != null) map.putAll(h);
			}
		}
		header.setHeaders(map);
		return header;
	}

	private static HttpHeader.HttpResponseHeader parseResponse(List<String> lines) {
		HttpHeader.HttpResponseHeader header = new HttpHeader.HttpResponseHeader();
		Map<String, String> map = new LinkedHashMap<>();

		if (!lines.isEmpty()) {
			String[] parts = lines.get(0).split(" ", 3);
			if (parts.length >= 2) {
				header.setProtocol(parts[0]);
				header.setStatus(parts[1] + (parts.length == 3 ? " " + parts[2] : ""));
			}
			for (int i = 1; i < lines.size(); i++) {
				Map<String, String> h = parseHeaderLine(lines.get(i));
				if (h != null) map.putAll(h);
			}
		}
		header.setHeaders(map);
		return header;
	}

	private static Map<String, String> parseHeaderLine(String line) {
		if (line.contains(":")) {
			String[] parts = line.split(":", 2);
			Map<String, String> map = new HashMap<>();
			map.put(parts[0].trim().toLowerCase(), parts[1].trim());
			return map;
		}
		return null;
	}

	private static void logHeader(String type, String part1, String part2, String part3, Map<String, String> headers) {
		log.debug("🟢 [{}]", type);
		if (part1 != null) log.debug("{}", part1);
		if (part2 != null) log.debug("{}", part2);
		if (part3 != null) log.debug("{}", part3);
		headers.forEach((k, v) -> log.debug("{}: {}", k, v));
	}

	@Data
	public static class HttpHeader {
		private Client client;
		private String agentString;
		private HttpRequestHeader requestHeader;
		private HttpResponseHeader responseHeader;

		@Data
		public static class HttpRequestHeader {
			private String method;
			private String url;
			private String protocol;
			private Map<String, String> headers;
		}

		@Data
		public static class HttpResponseHeader {
			private String protocol;
			private String status;
			private Map<String, String> headers;
		}
	}
}
