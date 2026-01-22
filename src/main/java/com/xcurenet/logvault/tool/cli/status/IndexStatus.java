package com.xcurenet.logvault.tool.cli.status;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "index", description = "OpenSearch Search & Status Utility")
public class IndexStatus implements Callable<Integer> {
	private static final String INDEX_STATUS = "https://localhost:%s/opensearch/indexStatus";

	@Override
	public Integer call() throws Exception {
		System.out.println(IndexCommon.getAPI(String.format(INDEX_STATUS, IndexCommon.getPort()), true));
		return 0;
	}
}
