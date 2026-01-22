package com.xcurenet.logvault.tool.cli.status;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "cluster", description = "OpenSearch Cluster Status Utility")
public class ClusterStatus implements Callable<Integer> {
	private static final String CLUSTER_STATUS = "https://localhost:%s/opensearch/clusterHealth";

	@Override
	public Integer call() throws Exception {
		System.out.println(IndexCommon.getAPI(String.format(CLUSTER_STATUS, IndexCommon.getPort()), false));
		return 0;
	}
}
