package com.xcurenet.logvault.tool.cli.status;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@Log4j2
public class IndexCommon {

	public static String getPort() throws IOException {
		ConfigurableEnvironment env = new StandardEnvironment();
		env.getPropertySources().addLast(new ResourcePropertySource(new ClassPathResource("application.properties")));
		return env.getProperty("server.port");
	}

	public static String getAPI(final String url, final boolean isArray) throws IOException {
		return getAPI(url, isArray, false);
	}

	public static String getAPI(final String url, final boolean isArray, boolean isNumber) throws IOException {
		Connection.Response res = Jsoup.connect(url)
				.sslSocketFactory(createTrustAllSslSocketFactory())
				.timeout(60_000)
				.method(Connection.Method.GET)
				.ignoreContentType(true)
				.ignoreHttpErrors(true)
				.execute();
		if (isNumber) return res.body();
		if (isArray) return JSONArray.parseArray(res.body()).toString(JSONWriter.Feature.PrettyFormat);
		return JSONObject.parseObject(res.body()).toString(JSONWriter.Feature.PrettyFormat);
	}

	private static SSLSocketFactory createTrustAllSslSocketFactory() {
		try {
			TrustManager[] trustAllCerts = new TrustManager[]{
					new X509TrustManager() {
						public java.security.cert.X509Certificate[] getAcceptedIssuers() {
							return null;
						}

						public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
						}

						public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
						}
					}
			};

			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, new TrustManager[]{new TrustAllManager()}, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
				public boolean verify(String hostname, javax.net.ssl.SSLSession sslSession) {
					return true;
				}
			});
			return sc.getSocketFactory();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static class TrustAllManager implements X509TrustManager {
		public void checkClientTrusted(X509Certificate[] c, String a) {
		}

		public void checkServerTrusted(X509Certificate[] c, String a) {
		}

		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}
}
