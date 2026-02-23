package com.xcurenet.logvault.conf;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Log4j2
@Component
@RequiredArgsConstructor
public class ConfigPrinter {

	private final Config config;
	private final ObjectMapper objectMapper;

	@EventListener
	public void onRefresh(RefreshScopeRefreshedEvent event) throws Exception {
		printConfig("REFRESH");
	}

	@PostConstruct
	public void init() throws Exception {
		printConfig("INIT");
	}

	public void printConfig(final String phase) throws Exception {
		Object target = config;
		if (AopUtils.isAopProxy(config) && config instanceof Advised advised) {
			target = advised.getTargetSource().getTarget();
		}
		log.info("===== CONFIG ({}) =====\n{}", phase, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.convertValue(target, Map.class)));
	}
}