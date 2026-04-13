package com.xcurenet.logvault.module.task.pipeline;

import com.xcurenet.logvault.opensearch.EmassDoc;

public interface PipelineWorker {
	String getTaskType();

	boolean isEnabled();

	boolean isTarget(EmassDoc doc);

	EmassDoc process(EmassDoc doc) throws Exception;

	int getWorkerCount();
}
