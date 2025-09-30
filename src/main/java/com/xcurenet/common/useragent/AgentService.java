package com.xcurenet.common.useragent;

import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AgentService {

	private static final Parser AGENT_PARSER = new Parser();
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
			client = AGENT_PARSER.parse(userAgent);
			UA_CACHE.put(userAgent, client);
		}
		return client;
	}
}
