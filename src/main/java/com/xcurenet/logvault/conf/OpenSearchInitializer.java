package com.xcurenet.logvault.conf;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.common.utils.ExFactory;
import com.xcurenet.logvault.exception.IndexerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.client.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StopWatch;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Log4j2
@Configuration
@RequiredArgsConstructor
public class OpenSearchInitializer {

	private final RestHighLevelClient client;

	private static final String POLICY_NAME = "emass_policy";
	private static final String POLICY_PATH = "opensearch/emass_policy.json";
	private static final String TEMPLATE_NAME = "emass-template";
	private static final String TEMPLATE_PATH = "opensearch/emass-template.json";

	private static final String ROOM_TEMPLATE_NAME = "aegis-room-template";
	private static final String ROOM_TEMPLATE_PATH = "opensearch/aegis-room-template.json";

	private static final String SHADOW_AI_TEMPLATE_NAME = "shadow-ai-template";
	private static final String SHADOW_AI_TEMPLATE_PATH = "opensearch/shadow-ai-template.json";

	/**
	 * OpenSearch 초기화 진입점
	 */
	public void init() {
		StopWatch sw = DateUtils.start();
		log.info("INIT_OPENSEARCH | START");

		try {
			deletePolicyIfExists();
			Common.sleep(1000);

			createPolicy(loadJson(POLICY_PATH));
			Common.sleep(2000);

			applyTemplate(loadJson(ROOM_TEMPLATE_PATH), ROOM_TEMPLATE_NAME);
			Common.sleep(2000);

			applyTemplate(loadJson(TEMPLATE_PATH), TEMPLATE_NAME);
			Common.sleep(2000);

			applyTemplate(loadJson(SHADOW_AI_TEMPLATE_PATH), SHADOW_AI_TEMPLATE_NAME);
			Common.sleep(2000);

			log.info("INIT_OPENSEARCH | END | {}", DateUtils.stop(sw));
		} catch (Exception e) {
			throw ExFactory.ex(IndexerException::new, ErrorCode.OPENSEARCH_INIT_FAIL, Map.of("stage", "init", "exception", e.getMessage()));
		}
	}

	/**
	 * ISM Policy 삭제 (존재할 경우만)
	 */
	private void deletePolicyIfExists() {
		RestClient lowClient = client.getLowLevelClient();
		Request request = new Request("DELETE", "/_plugins/_ism/policies/" + POLICY_NAME);
		try {
			Response response = lowClient.performRequest(request);
			log.info("CONF_OPENSEARCH | ISM POLICY [{}] deleted. Response: {}", POLICY_NAME, response.getStatusLine());
		} catch (ResponseException e) {
			if (e.getResponse().getStatusLine().getStatusCode() == 404) {
				log.info("CONF_OPENSEARCH | ISM POLICY [{}] does not exist. Skip delete.", POLICY_NAME);
				return;
			}
			throw ExFactory.ex(IndexerException::new, ErrorCode.OPENSEARCH_POLICY_DELETE_FAIL, Map.of("policy", POLICY_NAME, "exception", e.getMessage()));
		} catch (Exception e) {
			throw ExFactory.ex(IndexerException::new, ErrorCode.OPENSEARCH_POLICY_DELETE_FAIL, Map.of("policy", POLICY_NAME, "exception", e.getMessage()));
		}
	}

	/**
	 * ISM Policy 생성
	 */
	private void createPolicy(final String json) {
		try {
			Request request = new Request("PUT", "/_plugins/_ism/policies/" + POLICY_NAME);
			request.setJsonEntity(json);

			Response response = client.getLowLevelClient().performRequest(request);
			log.info("CONF_OPENSEARCH | ISM POLICY [{}] created. Response: {}", POLICY_NAME, response.getStatusLine());
		} catch (Exception e) {
			throw ExFactory.ex(IndexerException::new, ErrorCode.OPENSEARCH_POLICY_CREATE_FAIL, Map.of("policy", POLICY_NAME, "exception", e.getMessage()));
		}
	}

	/**
	 * Index Template 생성 또는 업데이트 (UPSERT)
	 */
	private void applyTemplate(final String json, final String name) {
		try {
			Request request = new Request("PUT", "/_index_template/" + name);
			request.setJsonEntity(json);

			Response response = client.getLowLevelClient().performRequest(request);
			log.info("CONF_OPENSEARCH | INDEX TEMPLATE [{}] applied. Response: {}", name, response.getStatusLine());
		} catch (Exception e) {
			throw ExFactory.ex(IndexerException::new, ErrorCode.OPENSEARCH_TEMPLATE_APPLY_FAIL, Map.of("template", name, "exception", e.getMessage()));
		}
	}

	/**
	 * classpath 리소스에서 JSON 파일 로딩
	 */
	private String loadJson(final String path) throws IndexerException {
		try {
			ClassPathResource resource = new ClassPathResource(path);
			try (InputStream in = resource.getInputStream()) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			throw ExFactory.ex(IndexerException::new, ErrorCode.OPENSEARCH_JSON_LOAD_FAIL, Map.of("path", path, "exception", e.getMessage()));
		}
	}
}
