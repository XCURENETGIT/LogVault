package com.xcurenet.logvault.conf;

import lombok.extern.log4j.Log4j2;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
public class JasyptConfig {

	private static final String ENCRYPTKEY = "xcurenet1!";
	private static final String ALGORITHM = "PBEWithMD5AndDES";

	@Bean(name = "jasyptStringEncryptor")
	public StringEncryptor stringEncryptor() {
		PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
		SimpleStringPBEConfig config = new SimpleStringPBEConfig();
		config.setPassword(ENCRYPTKEY);
		config.setAlgorithm(ALGORITHM);
		config.setKeyObtentionIterations("1000");
		config.setPoolSize("1");
		config.setProviderName("SunJCE");
		config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
		config.setStringOutputType("base64");
		encryptor.setConfig(config);
		return encryptor;
	}

	public static String decrypt(final String text) {
		if (text != null && text.startsWith("ENC(") && text.endsWith(")")) {
			String cipher = text.substring(4, text.length() - 1);
			return new JasyptConfig().stringEncryptor().decrypt(cipher);
		}
		return text;
	}


	/**
	 * jasypt encrypt
	 */
	public static void main(String[] args) {
		String password = "admin";

		JasyptConfig jasyptConfig = new JasyptConfig();
		StringEncryptor stringEncryptor = jasyptConfig.stringEncryptor();

		String encryptedText = stringEncryptor.encrypt(password);
		String decryptedText = stringEncryptor.decrypt(encryptedText);

		log.info("encryptedText > {}", encryptedText);
		log.info("decryptedText > {}", decryptedText);
	}
}