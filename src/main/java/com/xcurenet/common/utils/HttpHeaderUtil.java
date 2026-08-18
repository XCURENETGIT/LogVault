package com.xcurenet.common.utils;

import com.xcurenet.common.useragent.AgentService;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import ua_parser.Client;

import java.io.StringReader;
import java.util.*;

@Log4j2
public class HttpHeaderUtil {
	private static final AgentService AGENT_SERVICE = new AgentService();

	public static void main(String[] args) {
		String raw = """
				
				
				POST /v1/engines/copilot-codex/completions HTTP/1.1
				Content-Length: 32744
				Host: proxy.individual.githubcopilot.com
				Authorization: Bearer tid=e3802b3ce8edadaf3af761f2d9a1209c;exp=1756365239;sku=free_limited_copilot;proxy-ep=proxy.individual.githubcopilot.com;st=dotcom;chat=1;cit=1;malfil=1;editor_preview_features=1;agent_mode=1;mcp=1;ccr=1;rt=1;8kp=1;ip=1.225.49.118;asn=AS9318;cq=1323;rd=1757721600:cb2ffbb0a9f01a1bc8966ed96d4356da06143c160496d6d3430dd3efb0c1a5f5
				content-type: application/json
				Copilot-Language-Server-Version: 1.250.0
				Editor-Plugin-Version: copilot-intellij/1.5.30-231
				Editor-Version: JetBrains-IU/232.9559.62
				OpenAI-Intent: copilot-ghost
				Openai-Organization: github-copilot
				user-agent: GithubCopilot/1.250.0
				VScode-MachineId: fc68586d5371ff2adff3e7e5505ee6567749e3f0affda826f1f6ed9447f51408
				VScode-SessionId: 44e42baa-8f58-4368-9648-31371f2561651756340930361
				X-GitHub-Api-Version: 2024-12-15
				X-Request-Id: f0d6ebfb-b6ea-47ff-9fab-19ca4991f073
				
				
				
				""";
		System.out.println(parserHeader(raw));
	}

	public static HttpHeader parserHeader(final String raw) {
		if (raw == null || raw.isBlank()) return null;

		HttpHeader header = null;
		try {
			List<String> lines = IOUtils.readLines(new StringReader(raw));
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				if (line != null) {
					lines.set(i, line.replace("\r", ""));
				}
			}

			int firstNonEmpty = 0;
			while (firstNonEmpty < lines.size() && lines.get(firstNonEmpty).trim().isEmpty()) {
				firstNonEmpty++;
			}
			if (firstNonEmpty >= lines.size()) {
				log.warn("HTTP Header: 내용이 없이 공백만 존재함");
				return null;
			}
			lines = lines.subList(firstNonEmpty, lines.size());

			int splitIndex = -1;
			boolean seenHeaderLine = false;
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				if (line.trim().isEmpty()) {
					if (seenHeaderLine) {
						splitIndex = i;
						break;
					}
				} else {
					seenHeaderLine = true;
				}
			}

			List<String> requestLines;
			List<String> responseLines;
			if (splitIndex == -1) {
				requestLines = lines;
				responseLines = Collections.emptyList();
			} else {
				requestLines = lines.subList(0, splitIndex);
				int j = splitIndex + 1;
				while (j < lines.size() && lines.get(j).trim().isEmpty()) {
					j++;
				}
				if (j >= lines.size()) {
					responseLines = Collections.emptyList();
				} else {
					responseLines = lines.subList(j, lines.size());
				}
			}

			HttpHeader.HttpRequestHeader request = requestLines.isEmpty() ? null : parseRequest(requestLines);
			HttpHeader.HttpResponseHeader response = responseLines.isEmpty() ? null : parseResponse(responseLines);

			header = new HttpHeader();
			header.setRequestHeader(request);
			header.setResponseHeader(response);

			String ua = null;
			if (request != null && request.getHeaders() != null) {
				Map<String, String> headers = request.getHeaders();
				ua = headers.get("user-agent");
				if (ua == null) ua = headers.get("User-Agent");
			}
			header.setAgentString(ua);
			if (ua != null) {
				header.setClient(parse(ua));
			}

			if (request != null) {
				logHeader("Request", request.getMethod(), request.getUrl(), request.getProtocol(), request.getHeaders());
			}
			if (response != null) {
				logHeader("Response", response.getProtocol(), response.getStatus(), null, response.getHeaders());
			}

		} catch (Exception e) {
			log.warn("HTTP Header 파싱 실패. raw 일부: {}", abbreviate(raw, 200), e);
		}
		return header;
	}

	private static String abbreviate(String s, int max) {
		if (s == null || s.length() <= max) return s;
		return s.substring(0, max) + "...";
	}


	public static Client parse(final String userAgent) {
		return AGENT_SERVICE.parse(userAgent);
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
