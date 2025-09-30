package com.xcurenet.logvault;

import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.common.utils.FileTimeComparator;
import com.xcurenet.common.utils.NamedThreadFactory;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.scanner.Scanner;
import com.xcurenet.logvault.module.worker.*;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
@EnableScheduling
@SpringBootApplication
@RequiredArgsConstructor
@EnableTransactionManagement
@ComponentScan(basePackages = "com.xcurenet.*")
public class LogVaultApplication implements CommandLineRunner {

	private final ApplicationContext context;
	private final Config conf;

	@Getter
	protected static final AtomicInteger secBy10Count = new AtomicInteger();

	@Getter
	protected static final AtomicInteger minuteBy1Count = new AtomicInteger();

	public static final int QUEUE_CAPACITY = 1000;
	private final AtomicBoolean run = new AtomicBoolean(true);
	private final PriorityBlockingQueue<ScanData> wmailQueue = new PriorityBlockingQueue<>(QUEUE_CAPACITY, new FileTimeComparator());

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(LogVaultApplication.class);
		application.setRegisterShutdownHook(false);
		application.addListeners(new ApplicationPidFileWriter(new File(Config.PID_FILE)));
		application.run(args);

		ConfigurableApplicationContext ctx = application.run(args);
		Runtime.getRuntime().addShutdownHook(new Thread(ctx::close));
	}

	@Override
	public void run(String... args) throws Exception {
		final CountDownLatch shutdownLatch = new CountDownLatch(1);
		final List<AbstractLogVaultWorker> workers = new ArrayList<>();
		try {
			Runtime.getRuntime().addShutdownHook(new WaitForProperShutdown(shutdownLatch, run));
			startScanner();
			startWorker(workers);

			while (run.get()) {
				CommonUtil.sleep(1000);
			}
		} finally {
			while (!isCompleteWorkers(workers)) {
				CommonUtil.sleep(1000);
			}
			shutdownLatch.countDown();
		}
	}

	private boolean isCompleteWorkers(final List<AbstractLogVaultWorker> workers) {
		for (final AbstractLogVaultWorker worker : workers) {
			if (worker.getProgress()) return false;
		}
		return true;
	}

	@PreDestroy
	public void onShutdown() {
		log.info("LogVault for EMASS AI shutdown");
	}

	private void startScanner() {
		if (conf.isEnableWmail()) startScanner(conf.getDirWmail(), wmailQueue);
		log.info("[START_SCAN] LOAD END\n");
	}

	private void startScanner(final String dir, final PriorityBlockingQueue<ScanData> queue) {
		if (CommonUtil.isEmpty(dir)) return;

		ExecutorService executor = Executors.newFixedThreadPool(1);
		executor.execute(new Scanner(dir, queue, run, conf.getScanDirectoryScanningWaitingSec()));
		executor.shutdown();
		log.info("[START_SCAN] {}", dir);
	}

	private void startWorker(final List<AbstractLogVaultWorker> workers) throws Exception {
		if (conf.isEnableWmail()) startWorker(workers, wmailQueue, conf.getWorkerSizeWmail(), MSGWorker.class);
		log.info("[START_WORKER] LOAD END\n");
	}

	private void startWorker(final List<AbstractLogVaultWorker> workers, final PriorityBlockingQueue<ScanData> queue, int workerSize, Class<? extends AbstractLogVaultWorker> workerClass) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(workerSize, new NamedThreadFactory(workerClass.getSimpleName()));
		for (int i = 0; i < workerSize; i++) {
			AbstractLogVaultWorker worker = workerClass.getDeclaredConstructor(ApplicationContext.class, PriorityBlockingQueue.class, AtomicBoolean.class).newInstance(context, queue, run);
			workers.add(worker);
			executor.execute(worker);
		}
		log.info("[START_WORKER] {}", workerClass.getName());
		executor.shutdown();
	}
}