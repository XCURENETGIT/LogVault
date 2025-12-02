package com.xcurenet.logvault.module.task.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class TaskDispatcher {
	private final TaskProcessorRegistry registry;
	private final ThreadPoolTaskExecutor ocrExecutor;
	private final ThreadPoolTaskExecutor mlExecutor;
	private final TaskMessageRepository repository;

	public void dispatch(TaskMessage m) {
		TaskProcessor processor = registry.find(m.getTaskType());

		Runnable job = () -> {
			try {
				processor.process(m);
				repository.deleteById(m.getMsgId(), m.getTaskType());
			} catch (Exception e) {
				log.debug("DISPATCH | Task failed: {}", e.getMessage(), e);
				repository.updateStatusFailed(m.getMsgId(), m.getTaskType(), e.getMessage());
			}
		};

		String type = m.getTaskType();
		if (TaskDispatcherService.TASK_TYPE.OCR.name().equalsIgnoreCase(type)) {
			ocrExecutor.execute(job);
		} else if (TaskDispatcherService.TASK_TYPE.ML.name().equalsIgnoreCase(type)) {
			mlExecutor.execute(job);
		} else {
			mlExecutor.execute(job);
		}
	}

	public int remainingCapacityFor(TaskDispatcherService.TASK_TYPE taskType) {
		ThreadPoolTaskExecutor ex = TaskDispatcherService.TASK_TYPE.OCR.name().equalsIgnoreCase(taskType.name()) ? ocrExecutor : mlExecutor;
		int queueCap = ex.getThreadPoolExecutor().getQueue().remainingCapacity();
		int size = ex.getActiveCount();
		int headroom = Math.max(0, ex.getMaxPoolSize() - size);
		return queueCap + headroom;
	}
}
