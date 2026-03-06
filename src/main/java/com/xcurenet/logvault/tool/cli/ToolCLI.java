package com.xcurenet.logvault.tool.cli;

import com.xcurenet.logvault.tool.cli.attach.DecryptFile;
import com.xcurenet.logvault.tool.cli.crypto.CreateKey;
import com.xcurenet.logvault.tool.cli.crypto.Decrypt;
import com.xcurenet.logvault.tool.cli.crypto.Encrypt;
import com.xcurenet.logvault.tool.cli.reprocess.InsaReProcess;
import com.xcurenet.logvault.tool.cli.status.*;
import com.xcurenet.logvault.tool.cli.util.Base64Tool;
import picocli.CommandLine;

@CommandLine.Command(
		name = "LogVault Tool",
		mixinStandardHelpOptions = true,
		version = "Version : logvault_tool_1.0.0",
		description = "XCURENET Dev Toolbox",
		subcommands = {
				CreateKey.class,
				Encrypt.class,
				Decrypt.class,
				DecryptFile.class,
				Base64Tool.class,
				ClusterStatus.class,
				ShardStatus.class,
				IndexStatus.class,
				CountIndex.class,
				DayOfHourIndex.class,
				SearchId.class,
				SearchIndex.class,
				InsaReProcess.class
		}
)
public class ToolCLI implements Runnable {

	@Override
	public void run() {
		CommandLine.usage(this, System.out);
	}

	public static void main(String[] args) {
		//System.setProperty("logback.configurationFile", "logback-spring.xml");
		System.setProperty("spring.profiles.active", "cli");
		int exitCode = new CommandLine(new ToolCLI())
				.setCaseInsensitiveEnumValuesAllowed(true)
				.execute(args);
		System.exit(exitCode);
	}
}
