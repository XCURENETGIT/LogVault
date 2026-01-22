package com.xcurenet.logvault.tool.cli.crypto;

import com.xcurenet.crypto.Crypto;
import com.xcurenet.logvault.conf.Config;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "createkey",
		description = "Encryption Key Generation Utility")
public class CreateKey implements Callable<Integer> {

	@CommandLine.Parameters(
			arity = "1",
			paramLabel = "PlainText",
			description = "Text to encrypt keyfile; if omitted, read from STDIN")
	String input;

	@Override
	public Integer call() {
		if (Files.exists(Path.of(Config.getEncryptKeyFile()))) {
			System.out.println("The encryption key file already exists.");
			return 0;
		}
		Crypto.makeKeyFile(Config.getEncryptKeyFile(), input);
		return 0;
	}
}