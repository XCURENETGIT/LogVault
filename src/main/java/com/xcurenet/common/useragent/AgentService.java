package com.xcurenet.common.useragent;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Device;
import ua_parser.OS;
import ua_parser.Parser;
import ua_parser.UserAgent;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Log4j2
@Service
public class AgentService {

	private static final String FALLBACK_REGEX_RESOURCE = "/ua_parser/fallback-regexes.yaml";
	private static final Parser STANDARD_PARSER = new Parser();
	private static final Parser FALLBACK_PARSER = createFallbackParser();
	private static final int MAX_CACHE_SIZE = 100_000;

	// LRU Cache: 가장 오래된 항목 자동 제거
	private static final Map<String, Client> UA_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Client> eldest) {
			return size() > MAX_CACHE_SIZE;
		}
	});

	public Client parse(final String userAgent) {
		Client client = UA_CACHE.get(userAgent);
		if (client == null) {
			client = parseWithFallback(userAgent);
			UA_CACHE.put(userAgent, client);
		}
		return client;
	}

	private Client parseWithFallback(final String userAgent) {
		Client standardClient = safeParse(STANDARD_PARSER, userAgent);
		// 표준 파서가 인식한 값은 유지하되, 인식하지 못한 OS 정보는 보조 규칙으로 보완한다.
		if (standardClient != null
				&& !isUnrecognized(standardClient.userAgent)
				&& !isUnrecognized(standardClient.os)) return standardClient;

		if (FALLBACK_PARSER == null) return standardClient;

		Client fallbackClient = safeParse(FALLBACK_PARSER, userAgent);
		return merge(standardClient, fallbackClient);
	}

	private Client safeParse(final Parser parser, final String userAgent) {
		if (parser == null) return null;
		try {
			return parser.parse(userAgent);
		} catch (RuntimeException e) {
			log.warn("User-Agent parsing failed. Raw value will be preserved by the caller.", e);
			return null;
		}
	}

	private Client merge(final Client standardClient, final Client fallbackClient) {
		if (fallbackClient == null) return standardClient;
		if (standardClient == null) return fallbackClient;

		UserAgent userAgent = isUnrecognized(standardClient.userAgent)
				? fallbackClient.userAgent : standardClient.userAgent;
		OS os = isUnrecognized(standardClient.os) ? fallbackClient.os : standardClient.os;
		Device device = isUnrecognized(standardClient.device) ? fallbackClient.device : standardClient.device;
		return new Client(userAgent, os, device);
	}

	private boolean isUnrecognized(final UserAgent userAgent) {
		return userAgent == null || isOther(userAgent.family);
	}

	private boolean isUnrecognized(final OS os) {
		return os == null || isOther(os.family);
	}

	private boolean isUnrecognized(final Device device) {
		return device == null || isOther(device.family);
	}

	private boolean isOther(final String family) {
		return family == null || family.isBlank() || "other".equalsIgnoreCase(family);
	}

	private static Parser createFallbackParser() {
		try (InputStream inputStream = AgentService.class.getResourceAsStream(FALLBACK_REGEX_RESOURCE)) {
			if (inputStream == null) {
				log.warn("User-Agent fallback regex resource not found: {}", FALLBACK_REGEX_RESOURCE);
				return null;
			}
			return new Parser(inputStream);
		} catch (IOException | RuntimeException e) {
			log.warn("User-Agent fallback parser initialization failed. Standard parser will be used.", e);
			return null;
		}
	}
}
