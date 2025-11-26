package com.xcurenet.logvault.conf;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Log4j2
@Configuration
public class RestClientConfig {

	@Bean
	public RestClient restClient(Config conf) {
		log.info("REST_INIT | timeout: {}sec", conf.getExtractTextTimeoutSec());
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(5_000);
		factory.setReadTimeout(conf.getExtractTextTimeoutSec() * 1000);
		return RestClient.builder().requestFactory(factory).build();
	}
}
