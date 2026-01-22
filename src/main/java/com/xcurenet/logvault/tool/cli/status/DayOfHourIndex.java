package com.xcurenet.logvault.tool.cli.status;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "count_day_hour", description = "OpenSearch DAY OF HOUR  Count Utility")
public class DayOfHourIndex implements Callable<Integer> {
	private static final String QUERY_URL = "https://localhost:%s/opensearch/hour_count?query=%s&day=%s";

	@CommandLine.Option(names = {"-q", "--query"}, required = false, description = "Query: *:*", defaultValue = "*:*")
	private String query;

	@CommandLine.Option(names = {"-d", "--day"}, required = true, description = "Day: 20260124")
	private String day;

	@Override
	public Integer call() throws Exception {
		System.out.println(IndexCommon.getAPI(String.format(QUERY_URL, IndexCommon.getPort(), query, day), false, true));
		return 0;
	}
}
