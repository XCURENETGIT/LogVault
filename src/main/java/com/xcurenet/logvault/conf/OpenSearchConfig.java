package com.xcurenet.logvault.conf;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContexts;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.data.client.orhlc.OpenSearchRestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

@Configuration
public class OpenSearchConfig {

	@Value("${spring.opensearch.rest.uris:http://127.0.0.1:9200}")
	private String host;

	@Value("${spring.opensearch.rest.username:admin}")
	private String userName;

	@Value("${spring.opensearch.rest.password}")
	private String password;

	@Bean
	public RestHighLevelClient restHighLevelClient() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
		final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(userName, password));

		SSLContext sslContext = SSLContexts.custom().loadTrustMaterial((chain, authType) -> true).build();
		return new RestHighLevelClient(
				RestClient.builder(HttpHost.create(host))
						.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
								.setSSLContext(sslContext)
								.setDefaultCredentialsProvider(credentialsProvider)
								.setSSLHostnameVerifier((hostname, session) -> true)
						)
		);
	}

	@Bean
	public OpenSearchRestTemplate openSearchRestTemplate(RestHighLevelClient client) {
		return new OpenSearchRestTemplate(client);
	}
}