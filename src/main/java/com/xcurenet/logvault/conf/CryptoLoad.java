package com.xcurenet.logvault.conf;

import com.xcurenet.common.utils.Common;
import com.xcurenet.crypto.Crypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.io.Console;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Log4j2
@Component
@RequiredArgsConstructor
public class CryptoLoad {

	private final Config conf;

	public void loadEncryptKey() {
		if (conf.isEncryptEnable()) {
			Path keyFile = Paths.get(Config.getEncryptKeyFile());
			if (!Files.exists(keyFile)) {
				log.warn("LOAD_ENCRYPT | The file encryption setting is enabled, but no key file is available.");
				if (!makeKey()) {
					log.error("LOAD_ENCRYPT | Failed to generate the encryption key file: {}", conf.getEncryptKey());
					loadEncryptKey();
				}
			}

			final String key = Common.getKey();
			if (Common.isNotEmpty(key)) {
				log.info("LOAD_ENCRYPT | {} | {}", keyFile, conf.getEncryptCipher());
				conf.setEncryptKey(key);
			} else {
				log.error("LOAD_ENCRYPT | Invalid Key File: {}", conf.getEncryptKey());
				System.exit(1);
			}
		}
	}

	public static boolean makeKey() {
		Path keyPath = Paths.get(Config.getEncryptKeyFile());
		return Crypto.makeKeyFile(keyPath.toString(), inputPassword());
	}

	private static String inputPassword() {
		System.out.println("※ An encryption key must be generated to ensure system security.");
		Console console = System.console();
		String password;
		if (console != null) {
			while (true) {
				char[] firstInput = console.readPassword("Please enter the password: ");
				char[] confirmInput = console.readPassword("Please confirm your password: ");

				String pass1 = new String(firstInput);
				String pass2 = new String(confirmInput);
				if (pass1.equals(pass2)) {
					password = pass1;
					break;
				} else {
					System.out.println("❌ Passwords do not match. Please try again.\n");
				}
			}
		} else {
			java.util.Scanner scanner = new java.util.Scanner(System.in);
			while (true) {
				System.out.print("Please enter the password: ");
				String pass1 = scanner.nextLine();

				System.out.print("Please confirm your password: ");
				String pass2 = scanner.nextLine();
				if (pass1.equals(pass2)) {
					password = pass1;
					break;
				} else {
					System.out.println("❌ Passwords do not match. Please try again.\n");
				}
			}
		}
		System.out.println("✅ Password confirmed successfully!");
		return password;
	}
}
