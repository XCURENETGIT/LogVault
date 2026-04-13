package com.xcurenet.logvault.module.task.pipeline;

import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.alert.AlertService;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class AlertWorker implements PipelineWorker {

	private final AlertService alertService;
	private final Config conf;

	@Override
	public String getTaskType() {
		return "ALERT";
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public boolean isTarget(EmassDoc doc) {
		return true;
	}

	@Override
	public int getWorkerCount() {
		return conf.getTaskQueueAlertThreads();
	}

	@Override
	public EmassDoc process(EmassDoc doc) throws Exception {
		alertService.send(doc);
		return doc;
	}
}
