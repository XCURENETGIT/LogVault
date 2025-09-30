package com.xcurenet.logvault.conf;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;
import java.util.Map;

public class PropertySourceLoader implements EnvironmentPostProcessor {
	private static final String APPLICATION_NAME = "LogVault";
	private static final String CONNECTION_URL = "mongodb://emassailt:27018?replicaSet=shard1rs";

	private final Log log;

	public PropertySourceLoader(DeferredLogFactory logFactory) {
		this.log = logFactory.getLog(PropertySourceLoader.class);
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {

		Map<String, Object> props = new HashMap<>();
		Map<String, Object> config = DefaultConfig.getDefaultConfig();
		for (Map.Entry<String, Object> entry : config.entrySet()) {
			props.putIfAbsent(entry.getKey(), entry.getValue());
		}
		props.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> log.debug("✅ " + entry.getKey() + " " + entry.getValue()));
		PropertySource<Map<String, Object>> mongoSource = new MapPropertySource("mongoConfig", props);
		environment.getPropertySources().addFirst(mongoSource);

//		try (MongoClient mongoClient = MongoClients.create(CONNECTION_URL)) {
//			MongoDatabase database = mongoClient.getDatabase("venus");
//			MongoCollection<Document> collection = database.getCollection("APP_CONF");
//			Map<String, Object> props = new HashMap<>();
//			for (Document doc : collection.find(Filters.eq("app", APPLICATION_NAME))) {
//				props.put(doc.getString("key"), doc.get("value"));
//			}
//
//			Map<String, Object> config = DefaultConfig.getDefaultConfig();
//			for (Map.Entry<String, Object> entry : config.entrySet()) {
//				props.putIfAbsent(entry.getKey(), entry.getValue());
//			}
//			props.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> log.debug("✅ " + entry.getKey() + " " + entry.getValue()));
//			PropertySource<Map<String, Object>> mongoSource = new MapPropertySource("mongoConfig", props);
//			environment.getPropertySources().addFirst(mongoSource);
		log.info("Complete Configration Loading.");
//		} catch (Exception e) {
//			throw new RuntimeException("MongoDB 설정 로딩 실패", e);
//		}
	}
}

