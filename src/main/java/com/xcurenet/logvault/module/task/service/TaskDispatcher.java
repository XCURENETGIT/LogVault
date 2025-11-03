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
				repository.updateStatusDone(m.getMsgId()); // 성공 시 DONE
			} catch (Exception e) {
				log.error("[DISPATCH] Task failed: {} - {}", m.getMsgId(), e.getMessage(), e);
				repository.updateStatusFailed(m.getMsgId(), e.getMessage()); // 실패 시 FAILED + 에러 메시지
			}
		};

		String type = m.getTaskType();
		if ("OCR".equalsIgnoreCase(type)) {
			ocrExecutor.execute(job);
		} else if ("ML_ANALYSIS".equalsIgnoreCase(type)) {
			mlExecutor.execute(job);
		} else {
			// 기본: 현재는 ML풀로 보냄(원하면 타입별 풀 추가)
			mlExecutor.execute(job);
		}
	}

	public int remainingCapacityFor(String taskType) {
		ThreadPoolTaskExecutor ex = "OCR".equalsIgnoreCase(taskType) ? ocrExecutor : mlExecutor;
		// 남은 큐 용량 + (실행가능 스레드 여유) 정도로 추정
		int queueCap = ex.getThreadPoolExecutor().getQueue().remainingCapacity();
		int size = ex.getActiveCount();
		int headroom = Math.max(0, ex.getMaxPoolSize() - size);
		return queueCap + headroom;
	}
}
