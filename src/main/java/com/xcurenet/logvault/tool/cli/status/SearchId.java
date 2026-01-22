package com.xcurenet.logvault.tool.cli.status;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "search_id", description = "OpenSearch Message ID Search Utility")
public class SearchId implements Callable<Integer> {
	private static final String QUERY_URL = "https://localhost:%s/opensearch/search?query=%s&size=1";

	@CommandLine.Option(names = {"-i", "--id"}, required = true, description = "Message ID")
	private String id;

	@Override
	public Integer call() throws Exception {
		System.out.println(IndexCommon.getAPI(String.format(QUERY_URL, IndexCommon.getPort(), "_id:" + id), false));
		return 0;
	}
}
