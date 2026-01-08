package com.xcurenet.logvault.conf;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Log4j2
@Configuration
public class RestClientConfig {

	@Bean
	public RestClient restClient(Config conf) {
		log.info("REST_INIT | setConnectTimeout : 5sec | setReadTimeout: {}sec", conf.getExtractTextTimeoutSec());
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(5_000);
		factory.setReadTimeout(conf.getExtractTextTimeoutSec() * 1000);
		return RestClient.builder().requestFactory(factory).build();
	}

	@Bean
	@Qualifier("ocrRestTemplate")
	public RestTemplate ocrRestTemplate(Config conf) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(5_000);
		factory.setReadTimeout(conf.getOcrTimeoutSec() * 1000);
		return new RestTemplate(factory);
	}

	@Bean
	@Qualifier("mlRestTemplate")
	public RestTemplate mlRestTemplate(Config conf) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(5_000);
		factory.setReadTimeout(conf.getMlTimeoutSec() * 1000);
		return new RestTemplate(factory);
	}
}
