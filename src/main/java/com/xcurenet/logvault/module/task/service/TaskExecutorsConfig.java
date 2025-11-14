package com.xcurenet.logvault.module.task.service;

import com.xcurenet.logvault.conf.Config;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class TaskExecutorsConfig {
	private final Config conf;

	@Bean
	public BlockingQueue<TaskMessage> messageQueue() {
		return new LinkedBlockingQueue<>(conf.getTaskQueueWorkersCapacity());
	}

	@Bean(name = "ocrExecutor")
	public ThreadPoolTaskExecutor ocrExecutor() {
		ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
		AtomicInteger threadIndex = new AtomicInteger(0);
		ThreadFactory threadFactory = runnable -> {
			Thread t = new Thread(runnable);
			t.setName("TASK-OCR-" + threadIndex.getAndIncrement());
			return t;
		};
		ex.setThreadFactory(threadFactory);
		ex.setCorePoolSize(conf.getTaskQueueWorkersThreads());
		ex.setMaxPoolSize(conf.getTaskQueueWorkersThreads());
		ex.setQueueCapacity(50);
		ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		ex.initialize();
		return ex;
	}

	@Bean(name = "mlExecutor")
	public ThreadPoolTaskExecutor mlExecutor() {
		ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
		ex.setThreadNamePrefix("TASK-ML-");
		ex.setCorePoolSize(conf.getTaskQueueWorkersThreads());
		ex.setMaxPoolSize(conf.getTaskQueueWorkersThreads());
		ex.setQueueCapacity(50);
		ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		ex.initialize();
		return ex;
	}
}