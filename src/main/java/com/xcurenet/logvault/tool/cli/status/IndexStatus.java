package com.xcurenet.logvault.tool.cli.status;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import picocli.CommandLine;

import java.io.IOException;
import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "index",
		description = "OpenSearch Status Utilities")
public class IndexStatus implements Callable<Integer> {
	private enum Mode {cluster, shard, index}

	private static final String CLUSTER_STATUS = "http://127.0.0.1:%s/opensearch/clusterHealth";
	private static final String SHARD_STATUS = "http://127.0.0.1:%s/opensearch/shardStatus";
	private static final String INDEX_STATUS = "http://127.0.0.1:%s/opensearch/indexStatus";

	@CommandLine.Option(names = {"-m", "--mode"}, required = true, description = "Mode: ${COMPLETION-CANDIDATES}")
	private Mode mode;

	@Override
	public Integer call() throws Exception {
		String port = loadConf().getProperty("server.port");
		switch (mode) {
			case cluster -> System.out.println(getAPI(String.format(CLUSTER_STATUS, port), false));
			case shard -> System.out.println(getAPI(String.format(SHARD_STATUS, port), true));
			case index -> System.out.println(getAPI(String.format(INDEX_STATUS, port), true));
		}
		return 0;
	}

	private ConfigurableEnvironment loadConf() throws IOException {
		ConfigurableEnvironment env = new StandardEnvironment();
		env.getPropertySources().addLast(new ResourcePropertySource(new ClassPathResource("application.properties")));
		return env;
	}

	private String getAPI(final String url, final boolean isArray) throws IOException {
		Connection.Response res = Jsoup.connect(url)
				.timeout(60_000)
				.method(Connection.Method.GET)
				.ignoreContentType(true)
				.execute();
		if (isArray) return JSONArray.parseArray(res.body()).toString(JSONWriter.Feature.PrettyFormat);
		return JSONObject.parseObject(res.body()).toString(JSONWriter.Feature.PrettyFormat);
	}
}
