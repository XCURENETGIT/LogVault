package com.xcurenet.logvault.tool.cli.status;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "count", description = "OpenSearch Index Query Count Utility")
public class CountIndex implements Callable<Integer> {
	private static final String QUERY_URL = "https://localhost:%s/opensearch/search";

	@CommandLine.Option(names = {"-q", "--query"}, required = true, description = "Query: *:*")
	private String query;

	@Override
	public Integer call() throws Exception {
		System.out.println(IndexCommon.getAPI(String.format(QUERY_URL, IndexCommon.getPort()) + "?size=0&q=" + query, false, true));
		return 0;
	}
}
