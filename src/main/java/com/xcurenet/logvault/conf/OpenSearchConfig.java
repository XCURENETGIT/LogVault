package com.xcurenet.logvault.conf;

import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.opensearch.client.Node;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.data.client.orhlc.OpenSearchRestTemplate;
import org.opensearch.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

@Log4j2
@Configuration
public class OpenSearchConfig {

	@Value("${spring.opensearch.rest.uris:http://127.0.0.1:9200}")
	private String uris;

	@Value("${spring.opensearch.rest.username:admin}")
	private String userName;

	@Value("${spring.opensearch.rest.password}")
	private String password;

	// 타임아웃 기본값 (필요시 프로퍼티로 노출)
	@Value("${spring.opensearch.timeout.connect:5000}")
	private int connectTimeoutMs;

	@Value("${spring.opensearch.timeout.socket:30000}")
	private int socketTimeoutMs;

	@Value("${spring.opensearch.timeout.connection-request:5000}")
	private int connectionRequestTimeoutMs;

	@Value("${spring.opensearch.http.max-conn-total:200}")
	private int maxConnTotal;

	@Value("${spring.opensearch.http.max-conn-per-route:100}")
	private int maxConnPerRoute;


	private SSLContext insecureSslContext() {
		try {
			return SSLContexts.custom().loadTrustMaterial((chain, authType) -> true).build();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private HostnameVerifier insecureHostnameVerifier() {
		return (hostname, session) -> true;
	}

	@Bean(destroyMethod = "close")
	public RestHighLevelClient restHighLevelClient() throws Exception {
		BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(userName, password));
		HttpHost[] hosts = Arrays.stream(uris.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(URI::create)
				.map(u -> new HttpHost(u.getHost(), u.getPort() == -1 ? ("https".equalsIgnoreCase(u.getScheme()) ? 443 : 9200) : u.getPort(), u.getScheme()))
				.toArray(HttpHost[]::new);

		RestClientBuilder builder = RestClient.builder(hosts)
				.setRequestConfigCallback((RequestConfig.Builder rcb) -> rcb
						.setConnectTimeout(connectTimeoutMs)
						.setSocketTimeout(socketTimeoutMs)
						.setConnectionRequestTimeout(connectionRequestTimeoutMs)
				)
				.setHttpClientConfigCallback(hcb -> hcb
						.setDefaultCredentialsProvider(credentialsProvider)
						.setSSLContext(insecureSslContext())
						.setSSLHostnameVerifier(insecureHostnameVerifier())
						.setMaxConnTotal(maxConnTotal)
						.setMaxConnPerRoute(maxConnPerRoute)
						.setDefaultIOReactorConfig(IOReactorConfig.custom()
								.setIoThreadCount(Math.max(2, Runtime.getRuntime().availableProcessors() / 2))
								.setSoKeepAlive(true)
								.build())
				)
				.setFailureListener(new RestClient.FailureListener() {
					@Override
					public void onFailure(Node node) {
						log.warn("[OpenSearch] node failure: {}", node);
					}
				});
		return new RestHighLevelClient(builder);
	}

	@Bean
	public OpenSearchRestTemplate openSearchRestTemplate(RestHighLevelClient client) {
		return new OpenSearchRestTemplate(client);
	}
}