package com.xcurenet.logvault.conf;

import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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

	/**
	 * OpenSearch 초기화 진입점
	 */
	public void init() throws IOException {
		StopWatch sw = DateUtils.start();
		log.info("INIT_OPENSEARCH | START");

		deletePolicyIfExists();
		Common.sleep(1000);
		createPolicy(loadJson(POLICY_PATH));
		Common.sleep(2000);

		applyTemplate(loadJson(ROOM_TEMPLATE_PATH), ROOM_TEMPLATE_NAME);
		Common.sleep(2000);

		applyTemplate(loadJson(TEMPLATE_PATH), TEMPLATE_NAME);
		Common.sleep(2000);

		log.info("INIT_OPENSEARCH | END | {}", DateUtils.stop(sw));
	}

	/**
	 * ISM Policy 삭제 (존재할 경우만)
	 */
	private void deletePolicyIfExists() throws IOException {
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
			throw e;
		}
	}

	/**
	 * ISM Policy 생성
	 */
	private void createPolicy(final String json) throws IOException {
		Request request = new Request("PUT", "/_plugins/_ism/policies/" + POLICY_NAME);
		request.setJsonEntity(json);
		Response response = client.getLowLevelClient().performRequest(request);
		log.info("CONF_OPENSEARCH | ISM POLICY [{}] created. Response: {}", POLICY_NAME, response.getStatusLine());
	}

	/**
	 * Index Template 생성 또는 업데이트 (UPSERT)
	 */
	private void applyTemplate(final String json, final String name) throws IOException {
		Request request = new Request("PUT", "/_index_template/" + name);
		request.setJsonEntity(json);
		Response response = client.getLowLevelClient().performRequest(request);
		log.info("CONF_OPENSEARCH | INDEX TEMPLATE [{}] applied (create/update). Response: {}", name, response.getStatusLine());
	}

	/**
	 * classpath 리소스에서 JSON 파일 로딩
	 */
	private String loadJson(final String path) throws IOException {
		ClassPathResource resource = new ClassPathResource(path);
		try (InputStream in = resource.getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
