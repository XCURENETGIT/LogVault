package com.xcurenet.logvault.tool.cli.util;

import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "base64",
		description = "Base64 encode/decode")
public class Base64Tool implements Callable<Integer> {
	private enum Mode {encode, decode}

	@CommandLine.Option(names = {"-m", "--mode"}, required = true, description = "Mode: ${COMPLETION-CANDIDATES}")
	private Mode mode;

	@CommandLine.Parameters(arity = "0..1", paramLabel = "INPUT", description = "Input text; if omitted, read from STDIN")
	private String input;

	@Override
	public Integer call() throws Exception {
		String text = readAllFromStdinIfEmpty(input);
		switch (mode) {
			case encode -> System.out.println(Base64.getEncoder().encodeToString(text.getBytes()));
			case decode -> System.out.println(new String(Base64.getDecoder().decode(text)));
		}
		return 0;
	}

	private String readAllFromStdinIfEmpty(String value) throws Exception {
		if (value != null && !value.isEmpty()) return value;
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line).append('\n');
			}
		}
		return sb.toString().trim();
	}
}
