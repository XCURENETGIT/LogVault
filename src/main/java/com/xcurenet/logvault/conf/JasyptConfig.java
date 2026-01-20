package com.xcurenet.logvault.conf;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.ExFactory;
import com.xcurenet.logvault.exception.EncryptException;
import lombok.extern.log4j.Log4j2;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Log4j2
@Configuration
public class JasyptConfig {
	private static final String ALGORITHM = "PBEWithMD5AndDES";
	private static StringEncryptor cachedEncryptor;

	@Bean(name = "jasyptStringEncryptor")
	public StringEncryptor stringEncryptor() throws EncryptException {
		return getEncryptorInstance();
	}

	private static synchronized StringEncryptor getEncryptorInstance() throws EncryptException {
		if (cachedEncryptor != null) {
			return cachedEncryptor;
		}

		try {
			String key = Common.getKey();
			if (key == null || key.trim().isEmpty()) {
				throw ExFactory.ex(EncryptException::new, ErrorCode.ENC_KEY_FAIL, Map.of("keyFile", Config.getEncryptKeyFile()));
			}

			PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
			SimpleStringPBEConfig config = new SimpleStringPBEConfig();

			config.setPassword(key);
			config.setAlgorithm(ALGORITHM);
			config.setKeyObtentionIterations("1000");
			config.setPoolSize("1");
			config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
			config.setStringOutputType("base64");

			encryptor.setConfig(config);
			cachedEncryptor = encryptor;
			return cachedEncryptor;
		} catch (EncryptException e) {
			throw e;
		} catch (Exception e) {
			throw ExFactory.ex(EncryptException::new, ErrorCode.ENC_INIT_FAIL, Map.of("stage", "encryptor-init", "exception", e.getMessage()));
		}
	}

	public static String decrypt(final String text) throws EncryptException {
		if (text == null || !text.startsWith("ENC(") || !text.endsWith(")")) {
			return text;
		}

		String cipher = text.substring(4, text.length() - 1);
		try {
			return getEncryptorInstance().decrypt(cipher);
		} catch (EncryptException e) {
			System.err.println(ErrorCode.ENC_DECRYPT_FAIL);
			e.printStackTrace(System.err);
			log.fatal("{} | {}", ErrorCode.ENC_DECRYPT_FAIL, e);
			throw ExFactory.ex(EncryptException::new, ErrorCode.ENC_DECRYPT_FAIL, Map.of("cipher", cipher));
		} catch (Exception e) {
			System.err.println(ErrorCode.ENC_INTERNAL_ERROR);
			e.printStackTrace(System.err);
			log.fatal("{} |", ErrorCode.ENC_INTERNAL_ERROR, e);
			throw ExFactory.ex(EncryptException::new, ErrorCode.ENC_INTERNAL_ERROR, Map.of("cipher", cipher, "exception", e.getMessage()));
		}
	}

	/**
	 * 단독 실행 테스트
	 */
	public static void main(String[] args) {
		try {
			String password = "root";
			StringEncryptor encryptor = new JasyptConfig().stringEncryptor();

			String encrypted = encryptor.encrypt(password);
			String decrypted = encryptor.decrypt(encrypted);
			log.info("encryptedText={}", encrypted);
			log.info("decryptedText={}", decrypted);
		} catch (EncryptException e) {
			log.error("", e);
		}
	}
}
