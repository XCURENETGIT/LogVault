package com.xcurenet.logvault.tool.cli.status;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "search", description = "OpenSearch Query Utility")
public class SearchIndex implements Callable<Integer> {
	private static final String QUERY_URL = "https://localhost:%s/opensearch/search";

	@CommandLine.Option(names = {"-q", "--query"}, required = true, description = "Query: *:*")
	private String query;

	@CommandLine.Option(names = {"-s", "--size"}, required = false, description = "size")
	private Integer size;

	@Override
	public Integer call() throws Exception {
		if (size == null || size == 0) size = 1;
		System.out.println(IndexCommon.getAPI(String.format(QUERY_URL, IndexCommon.getPort()) + "?size=" + size + "&q=" + query, false));
		return 0;
	}
}
