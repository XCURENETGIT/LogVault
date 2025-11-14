package com.xcurenet.logvault.conf;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class PropertySourceLoader implements EnvironmentPostProcessor, Ordered {
	private final Log log;
	private static final String QUERY = "SELECT CONF_ID, NVL(VAL, DEFAULT_VAL) AS VAL FROM UI_CONF WHERE APP_CD = ? AND USE_YN =?";

	public PropertySourceLoader(DeferredLogFactory logFactory) {
		this.log = logFactory.getLog(PropertySourceLoader.class);
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
		String url = env.getProperty("spring.datasource.url");
		String user = JasyptConfig.decrypt(env.getProperty("spring.datasource.username"));
		String pass = JasyptConfig.decrypt(env.getProperty("spring.datasource.password"));
		String driver = env.getProperty("spring.datasource.driver-class-name", "org.mariadb.jdbc.Driver");
		String appName = env.getProperty("spring.application.name", "LogVault");

		Map<String, Object> props = new HashMap<>();
		if (url != null && user != null) {
			try {
				Class.forName(driver); // 일부 환경에서 필요
				try (Connection conn = DriverManager.getConnection(url, user, pass); PreparedStatement ps = conn.prepareStatement(QUERY)) {
					ps.setString(1, appName);
					ps.setString(2, "Y");
					try (ResultSet rs = ps.executeQuery()) {
						while (rs.next()) {
							String key = rs.getString("CONF_ID");
							String val = rs.getString("VAL");
							props.put(key, val);
							log.info("✅ DB CONF : " + key + " = " + val);
						}
					}
				}
				log.info("LOAD_CONF | Loaded properties for app=" + appName + " from MariaDB.");
			} catch (Exception e) {
				String msg = "LOAD_CONF | Failed to load properties from MariaDB: " + e.getMessage();
				log.warn(msg + " — using defaults only.");
			}
		} else {
			log.warn("LOAD_CONF | spring.datasource.url/username not set — using defaults only.");
		}

		props.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(en -> log.debug("✅ " + en.getKey() + " = " + en.getValue()));
		PropertySource<Map<String, Object>> dbSource = new MapPropertySource("dbConfig", props);
		env.getPropertySources().addFirst(dbSource);
		log.info("LOAD_CONF | Complete Configuration Loading (DB + defaults).");
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}
}
