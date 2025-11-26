package com.xcurenet.logvault.conf;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.ExFactory;
import com.xcurenet.logvault.exception.EncryptException;
import com.xcurenet.logvault.exception.FileSendException;
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
		if (cachedEncryptor == null) {
			PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
			SimpleStringPBEConfig config = new SimpleStringPBEConfig();

			String key = Common.getKey();
			if (key == null || key.trim().isEmpty()) {
				throw ExFactory.ex(EncryptException::new, ErrorCode.ENC_KEY_FAIL, Map.of("detail", Config.getEncryptKeyFile()));
			}
			config.setPassword(key);
			config.setAlgorithm(ALGORITHM);
			config.setKeyObtentionIterations("1000");
			config.setPoolSize("1");
			config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
			config.setStringOutputType("base64");
			encryptor.setConfig(config);

			cachedEncryptor = encryptor;
			log.info("JASYPT Encryptor initialized successfully.");
		}
		return cachedEncryptor;
	}

	public static String decrypt(final String text) throws EncryptException {
		if (text != null && text.startsWith("ENC(") && text.endsWith(")")) {
			String cipher = text.substring(4, text.length() - 1);
			try {
				return getEncryptorInstance().decrypt(cipher);
			} catch (EncryptException e) {
				throw new RuntimeException(e);
			} catch (Exception e) {
				log.error("Failed to decrypt value. Cipher: {}, Error: {}", cipher, e.getMessage());
				throw new IllegalStateException("Decryption failed. Check encryption key match.", e);
			}
		}
		return text;
	}


	/**
	 * jasypt encrypt
	 */
	public static void main(String[] args) {
		String password = "NewPassword1e3!";

		JasyptConfig jasyptConfig = new JasyptConfig();
		StringEncryptor stringEncryptor = jasyptConfig.stringEncryptor();

		String encryptedText = stringEncryptor.encrypt(password);
		String decryptedText = stringEncryptor.decrypt(encryptedText);

		log.info("encryptedText > {}", encryptedText);
		log.info("decryptedText > {}", decryptedText);
	}
}