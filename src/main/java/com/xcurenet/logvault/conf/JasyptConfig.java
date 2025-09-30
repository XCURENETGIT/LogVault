package com.xcurenet.logvault.conf;

import lombok.extern.log4j.Log4j2;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
public class JasyptConfig {

	@Bean(name = "jasyptStringEncryptor")
	public StringEncryptor stringEncryptor() {
		String key = "xcure_ld1";
		PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
		SimpleStringPBEConfig config = new SimpleStringPBEConfig();
		config.setPassword(key); // 암호화할 때 사용하는 키
		config.setAlgorithm("PBEWithMD5AndDES"); // 암호화 알고리즘
		config.setKeyObtentionIterations("1000"); // 반복할 해싱 회수
		config.setPoolSize("1"); // 인스턴스 pool
		config.setProviderName("SunJCE");
		config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator"); // salt 생성 클래스
		config.setStringOutputType("base64"); // 인코딩 방식
		encryptor.setConfig(config);
		return encryptor;
	}

	/**
	 * jasypt encrypt
	 */
	public static void main(String[] args) {
		String password = "";
		StandardPBEStringEncryptor jasypt = new StandardPBEStringEncryptor();
		jasypt.setPassword("xcurenet_emass");
		jasypt.setAlgorithm("PBEWithMD5AndDES");
		String encryptedText = jasypt.encrypt(password);
		String decryptedText = jasypt.decrypt(encryptedText);

		log.info("encryptedText > {}", encryptedText);
		log.info("decryptedText > {}", decryptedText);
	}
}