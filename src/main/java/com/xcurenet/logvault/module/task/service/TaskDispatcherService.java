package com.xcurenet.logvault.module.task.service;

import com.xcurenet.logvault.conf.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class TaskDispatcherService {
	private final Config conf;
	private final TaskMessageRepository repo;
	private final TaskDispatcher dispatcher;

	@Scheduled(fixedDelayString = "${task.queue.scheduler.interval-ms:2000}")
	public void dispatch() {
		feedOneType("OCR");
		feedOneType("ML_ANALYSIS");
	}

	private void feedOneType(String type) {
		int capacity = Math.max(0, dispatcher.remainingCapacityFor(type));
		if (capacity == 0) return;

		int toFetch = Math.min(conf.getTaskQueueSchedulerFetchSize(), capacity);
		if (toFetch <= 0) return;

		// PENDING -> RUNNING으로 상태 전환하면서 배치 클레임
		List<TaskMessage> batch = repo.claimBatchByType(type, toFetch);
		if (batch.isEmpty()) return;

		for (TaskMessage m : batch) {
			dispatcher.dispatch(m);
		}
		log.debug("[FEED] type={}, fetched={}", type, batch.size());
	}
}
