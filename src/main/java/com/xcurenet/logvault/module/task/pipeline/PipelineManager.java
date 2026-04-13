package com.xcurenet.logvault.module.task.pipeline;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.task.service.TaskMessage;
import com.xcurenet.logvault.module.task.service.TaskMessageRepository;
import com.xcurenet.logvault.module.util.ActionType;
import com.xcurenet.logvault.opensearch.EmassDoc;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 후처리 파이프라인 매니저.
 * <ul>
 *   <li>순서 정의 — {@code task.pipeline.order} 설정</li>
 *   <li>진입점 — {@link #send(ScanData)} (MSGWorker가 호출)</li>
 *   <li>Runner 관리 — 타입별 자체 Scanner + Worker풀</li>
 * </ul>
 */
@Log4j2
@Component
@EnableScheduling
public class PipelineManager {

	private final Config conf;
	private final TaskMessageRepository repository;
	private final ObjectMapper mapper;

	// 순서
	@Value("${task.pipeline.order:OCR,ML,ALERT}")
	private String orderConfig;
	private List<String> order;

	// Worker 맵
	private final Map<String, PipelineWorker> workerMap;

	// Runner 목록
	private final List<Runner> runners = new ArrayList<>();

	public PipelineManager(Config conf, TaskMessageRepository repository, ObjectMapper mapper, List<PipelineWorker> workers) {
		this.conf = conf;
		this.repository = repository;
		this.mapper = mapper;
		this.workerMap = workers.stream().collect(Collectors.toMap(w -> w.getTaskType().toUpperCase(), Function.identity()));
	}

	// ===== 초기화 / 종료 =====
	public void start() {
		// 순서 파싱
		this.order = Arrays.stream(orderConfig.split(",")).map(String::trim).map(String::toUpperCase).filter(s -> !s.isEmpty()).toList();
		log.info("PIPELINE_ORDER | {}", String.join(" → ", order));

		// 재시작 복구
		repository.updateStatusPending();

		// 타입별 Runner 생성 및 시작
		for (String taskType : order) {
			PipelineWorker worker = workerMap.get(taskType);
			if (worker == null) {
				log.warn("PIPELINE_SKIP | No worker: {}", taskType);
				continue;
			}

			Runner runner = new Runner(taskType, worker);
			runner.start(worker.getWorkerCount(), conf.getTaskQueueSchedulerIntervalMs());
			runners.add(runner);
		}
		log.info("PIPELINE_START | {} runners", runners.size());
	}

	@PreDestroy
	public void stop() {
		runners.forEach(Runner::stop);
		log.info("PIPELINE_STOP | all runners stopped");
	}

	@Scheduled(cron = "0 0 3 * * *")
	private void cleanup() {
		if (Common.isWindow()) return;
		StopWatch sw = DateUtils.start();
		repository.deleteOldFailed();
		log.info("RM_FAILED | {}", DateUtils.stop(sw));
	}

	// ===== 순서 =====

	public String first() {
		return order.isEmpty() ? null : order.get(0);
	}

	public String next(String current) {
		int idx = order.indexOf(current.toUpperCase());
		return (idx >= 0 && idx < order.size() - 1) ? order.get(idx + 1) : null;
	}

	// ===== 진입점 (MSGWorker → 여기) =====

	public boolean send(final ScanData data) {
		EmassDoc doc = data.getEmassDoc();
		if (Common.isNotEquals(doc.getService().getSvc3(), "S")) return false;
		if (doc.getAction() != ActionType.ALLOW) return false;

		String firstType = first();
		if (firstType == null) return false;

		TaskMessage msg = new TaskMessage();
		msg.setMsgId(doc.getMsgid());
		msg.setTaskType(firstType);
		msg.setData(JSON.toJSONString(doc));

		StopWatch sw = DateUtils.start();
		repository.insertMessage(msg);
		log.info("PPS_SEND | {} | {}", firstType, DateUtils.stop(sw));
		return true;
	}

	// ===== Runner (내부 클래스) — Scanner + Worker풀 =====

	private class Runner {
		private final String taskType;
		private final PipelineWorker worker;
		private final BlockingQueue<TaskMessage> queue = new LinkedBlockingQueue<>(200);
		private final AtomicBoolean running = new AtomicBoolean(true);
		private ScheduledExecutorService scannerExec;
		private ExecutorService workerExec;

		Runner(String taskType, PipelineWorker worker) {
			this.taskType = taskType;
			this.worker = worker;
		}

		void start(int workerCount, long scanIntervalMs) {
			// Scanner 1개
			scannerExec = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "SCAN-" + taskType));
			scannerExec.scheduleWithFixedDelay(this::scan, 1000, scanIntervalMs, TimeUnit.MILLISECONDS);

			// Worker N개
			AtomicInteger idx = new AtomicInteger(0);
			workerExec = Executors.newFixedThreadPool(workerCount, r -> new Thread(r, taskType + "-WORKER-" + idx.getAndIncrement()));
			for (int i = 0; i < workerCount; i++) workerExec.submit(this::workerLoop);

			log.info("RUNNER_START | {} | scanner=1 workers={}", taskType, workerCount);
		}

		void stop() {
			running.set(false);
			if (scannerExec != null) scannerExec.shutdownNow();
			if (workerExec != null) workerExec.shutdownNow();
		}

		// --- Scanner ---
		private void scan() {
			try {
				int cap = queue.remainingCapacity();
				if (cap <= 0) return;
				List<TaskMessage> batch = repository.claimBatchByType(taskType, Math.min(50, cap));
				if (batch.isEmpty()) return;
				for (TaskMessage m : batch) {
					repository.updateStatusRunning(m.getMsgId(), taskType);
					queue.put(m);
				}
				log.debug("SCAN | {} | fetched={}", taskType, batch.size());
			} catch (Exception e) {
				log.error("SCAN_ERR | {} | {}", taskType, e.getMessage(), e);
			}
		}

		// --- Worker ---
		private void workerLoop() {
			while (running.get()) {
				TaskMessage m = null;
				try {
					m = queue.poll(1, TimeUnit.SECONDS);
					if (m == null) continue;
					MDC.put("worker", Thread.currentThread().getName());
					MDC.put("msgId", m.getMsgId());
					process(m);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				} catch (Exception e) {
					if (m != null) {
						log.error("TASK_FAIL | {} | {} | {}", m.getMsgId(), taskType, e.getMessage(), e);
						repository.updateStatusFailed(m.getMsgId(), taskType, e.getMessage());
					}
				} finally {
					MDC.remove("msgId");
				}
			}
		}

		private void process(TaskMessage m) throws Exception {
			long t0 = System.currentTimeMillis();
			EmassDoc doc = mapper.readValue(m.getData(), EmassDoc.class);

			if (worker.isEnabled() && worker.isTarget(doc)) {
				log.info("TASK_EXEC | {} | {}", m.getMsgId(), taskType);
				doc = worker.process(doc);
				log.info("TASK_DONE | {} | {} | {}ms", m.getMsgId(), taskType, System.currentTimeMillis() - t0);
			} else {
				log.info("TASK_PASS | {} | {}", m.getMsgId(), taskType);
			}

			// 다음 Step
			String nextType = next(taskType);
			if (nextType != null) {
				TaskMessage next = new TaskMessage();
				next.setMsgId(m.getMsgId());
				next.setTaskType(nextType);
				next.setData(JSON.toJSONString(doc));
				repository.insertMessage(next);
				log.info("TASK_NEXT | {} | {} → {}", m.getMsgId(), taskType, nextType);
			}

			repository.deleteById(m.getMsgId(), taskType);
		}
	}
}
