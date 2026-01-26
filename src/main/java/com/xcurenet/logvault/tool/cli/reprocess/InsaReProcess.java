package com.xcurenet.logvault.tool.cli.reprocess;

import com.xcurenet.logvault.tool.cli.status.IndexCommon;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "reprocess_insa", description = "Refresh HR information with the latest data for a given date")
public class InsaReProcess implements Callable<Integer> {
	private static final String QUERY_URL = "https://localhost:%s/opensearch/reprocessinsa?start=%s&end=%s";

	@CommandLine.Option(names = {"-s", "--start"}, required = true, description = "yyyymmdd")
	private String start;

	@CommandLine.Option(names = {"-e", "--end"}, required = true, description = "yyyymmdd")
	private String end;

	@Override
	public Integer call() throws Exception {
		if (start.length() != 8 || end.length() != 8) {
			System.out.println("Invalid start end date format {yyyymmdd}");
			return 0;
		}

		System.out.println(IndexCommon.getAPI(String.format(QUERY_URL, IndexCommon.getPort(), start, end), false));
		return 0;
	}
}
