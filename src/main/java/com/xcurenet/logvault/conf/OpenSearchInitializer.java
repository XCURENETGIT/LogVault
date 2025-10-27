package com.xcurenet.logvault.conf;

import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.client.*;
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


	@PostConstruct
	public void init() throws IOException {
		StopWatch sw = DateUtils.start();
		log.info("[INIT_OPENSEARCH] START");
		if (existsPolicy()) log.info("ISM POLICY [{}] already exists. Skipping creation.", OpenSearchInitializer.POLICY_NAME);
		else {
			createPolicy(loadJson(OpenSearchInitializer.POLICY_PATH));
			Common.sleep(2000);
		}

		if (existsTemplate()) log.info("INDEX TEMPLATE [{}] already exists. Skipping creation.", OpenSearchInitializer.TEMPLATE_NAME);
		else {
			createTemplate(loadJson(OpenSearchInitializer.TEMPLATE_PATH));
			Common.sleep(2000);
		}
		log.info("[INIT_OPENSEARCH] END | {}\n", DateUtils.stop(sw));
	}

	/**
	 * 템플릿 존재 여부
	 */
	private boolean existsTemplate() throws IOException {
		RestClient lowClient = client.getLowLevelClient();
		Request request = new Request("GET", "/_index_template/" + OpenSearchInitializer.TEMPLATE_NAME);
		try {
			Response response = lowClient.performRequest(request);
			return response.getStatusLine().getStatusCode() == 200;
		} catch (ResponseException e) {
			if (e.getResponse().getStatusLine().getStatusCode() == 404) return false;
			throw e;
		}
	}

	/**
	 * 템플릿 생성
	 */
	private void createTemplate(String json) throws IOException {
		Request request = new Request("PUT", "/_index_template/" + OpenSearchInitializer.TEMPLATE_NAME);
		request.setJsonEntity(json);
		Response response = client.getLowLevelClient().performRequest(request);
		log.info("INDEX TEMPLATE [{}] created. Response: {}", OpenSearchInitializer.TEMPLATE_NAME, response.getStatusLine());
	}

	/**
	 * 정책 존재 여부 확인
	 */
	private boolean existsPolicy() throws IOException {
		RestClient lowClient = client.getLowLevelClient();
		Request request = new Request("GET", "/_plugins/_ism/policies/" + OpenSearchInitializer.POLICY_NAME);
		try {
			Response response = lowClient.performRequest(request);
			return response.getStatusLine().getStatusCode() == 200;
		} catch (ResponseException e) {
			if (e.getResponse().getStatusLine().getStatusCode() == 404) return false;
			throw e;
		}
	}

	/**
	 * 정책 생성
	 */
	private void createPolicy(String json) throws IOException {
		Request request = new Request("PUT", "/_plugins/_ism/policies/" + OpenSearchInitializer.POLICY_NAME);
		request.setJsonEntity(json);
		Response response = client.getLowLevelClient().performRequest(request);
		log.info("ISM POLICY [{}] created. Response: {}", OpenSearchInitializer.POLICY_NAME, response.getStatusLine());
	}

	/**
	 * 리소스에서 JSON 읽기
	 */
	private String loadJson(final String path) throws IOException {
		ClassPathResource resource = new ClassPathResource(path);
		try (InputStream in = resource.getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
