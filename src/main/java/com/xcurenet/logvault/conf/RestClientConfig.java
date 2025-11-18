package com.xcurenet.logvault.conf;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class RestClientConfig {
	private final Config conf;

	@Bean
	public RestClient restClient(Config conf) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(5_000);
		factory.setReadTimeout(conf.getExtractTextTimeoutSec() * 1000);
		return RestClient.builder().requestFactory(factory).build();
	}
}
