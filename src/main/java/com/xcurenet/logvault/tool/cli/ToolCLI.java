package com.xcurenet.logvault.tool.cli;

import com.xcurenet.logvault.tool.cli.crypto.Decrypt;
import com.xcurenet.logvault.tool.cli.crypto.Encrypt;
import com.xcurenet.logvault.tool.cli.sample.SampleData;
import com.xcurenet.logvault.tool.cli.status.IndexStatus;
import com.xcurenet.logvault.tool.cli.util.Base64Tool;
import picocli.CommandLine;

@CommandLine.Command(
		name = "LogVault Tool",
		mixinStandardHelpOptions = true,
		version = "Version : 1.0",
		description = "XCURENET Dev Toolbox",
		subcommands = {
				Encrypt.class,
				Decrypt.class,
				Base64Tool.class,
				IndexStatus.class,
				SampleData.class
		}
)
public class ToolCLI implements Runnable {

	@Override
	public void run() {
		CommandLine.usage(this, System.out);
	}

	public static void main(String[] args) {
		int exitCode = new CommandLine(new ToolCLI())
				.setCaseInsensitiveEnumValuesAllowed(true)
				.execute(args);
		System.exit(exitCode);
	}
}
