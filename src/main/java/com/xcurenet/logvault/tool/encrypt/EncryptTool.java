package com.xcurenet.logvault.tool.encrypt;

import com.xcurenet.logvault.conf.JasyptConfig;

public class EncryptTool {

	public static void main(String[] args) {
		if (args.length < 1) {
			printUsage();
			return;
		}

		JasyptConfig jasyptConfig = new JasyptConfig();
		switch (args[0]) {
			case "encrypt" -> System.out.println(jasyptConfig.stringEncryptor().encrypt(args[1]));
			case "decrypt" -> System.out.println(jasyptConfig.stringEncryptor().decrypt(args[1]));
			default -> System.out.println("Unknown command: " + args[0]);
		}
	}

	private static void printUsage() {
		System.out.println("""
				Usage:
				  java -cp logvault.war com.xcurenet.logvault.tool.encrypt.EncryptTool encrypt xcurenet1!
				
				Commands:
				  encrypt xcurenet1!
				  decrypt jfPy4J6dh7ODZbX1LcOGvwBs8iYbN3uw
				""");
	}
}
