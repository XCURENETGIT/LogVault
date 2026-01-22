package com.xcurenet.logvault.tool.cli.status;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "shard", description = "OpenSearch Shard Status Utility")
public class ShardStatus implements Callable<Integer> {
	private static final String SHARD_STATUS = "https://localhost:%s/opensearch/shardStatus";

	@Override
	public Integer call() throws Exception {
		System.out.println(IndexCommon.getAPI(String.format(SHARD_STATUS, IndexCommon.getPort()), true));
		return 0;
	}
}
