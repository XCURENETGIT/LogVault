package com.xcurenet.logvault.tool.cli.crypto;

import com.xcurenet.logvault.conf.JasyptConfig;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "decrypt",
		description = "Decrypt Utilities")
public class Decrypt implements Callable<Integer> {

	@CommandLine.Parameters(
			arity = "1",
			paramLabel = "PlainText",
			description = "Text to decrypt; if omitted, read from STDIN")
	String input;

	@Override
	public Integer call() {
		JasyptConfig jasyptConfig = new JasyptConfig();
		System.out.println(jasyptConfig.stringEncryptor().decrypt(input));
		return 0;
	}
}