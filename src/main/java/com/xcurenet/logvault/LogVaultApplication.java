package com.xcurenet.logvault;

import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.FileTimeComparator;
import com.xcurenet.common.utils.NamedThreadFactory;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.scanner.FileScanner;
import com.xcurenet.logvault.module.worker.AbstractWorker;
import com.xcurenet.logvault.module.worker.MSGWorker;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.mybatis.spring.annotation.MapperScan;
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
import java.nio.file.Paths;
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
@ComponentScan(basePackages = "com.xcurenet")
@MapperScan("com.xcurenet")
public class LogVaultApplication implements CommandLineRunner {

	private final ApplicationContext context;
	private final Config conf;

	@Getter
	protected static final AtomicInteger secBy10Count = new AtomicInteger();

	@Getter
	protected static final AtomicInteger minuteBy1Count = new AtomicInteger();

	private final AtomicInteger scannerCount = new AtomicInteger();

	public static final int QUEUE_CAPACITY = 1000;
	private final AtomicBoolean run = new AtomicBoolean(true);
	private final PriorityBlockingQueue<ScanData> wmailQueue = new PriorityBlockingQueue<>(QUEUE_CAPACITY, new FileTimeComparator());
	private final PriorityBlockingQueue<ScanData> wmailBlockQueue = new PriorityBlockingQueue<>(QUEUE_CAPACITY, new FileTimeComparator());

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(LogVaultApplication.class);
		application.setRegisterShutdownHook(false);
		application.addListeners(new ApplicationPidFileWriter(Paths.get(Config.PID_FILE).toFile()));

		ConfigurableApplicationContext ctx = application.run(args);
		Runtime.getRuntime().addShutdownHook(new Thread(ctx::close));
	}

	@Override
	public void run(String... args) throws Exception {
		final CountDownLatch shutdownLatch = new CountDownLatch(1);
		final List<AbstractWorker> workers = new ArrayList<>();
		try {
			Runtime.getRuntime().addShutdownHook(new WaitForProperShutdown(shutdownLatch, run));
			startScanner();
			startWorker(workers);

			while (run.get()) {
				Common.sleep(1000);
			}
		} finally {
			while (!isCompleteWorkers(workers)) {
				Common.sleep(1000);
			}
			shutdownLatch.countDown();
		}
	}

	private boolean isCompleteWorkers(final List<AbstractWorker> workers) {
		for (final AbstractWorker worker : workers) {
			if (worker.getProgress()) return false;
		}
		return true;
	}

	@PreDestroy
	public void onShutdown() {
		log.info("LogVault for EMASS AI shutdown");
	}

	private void startScanner() {
		if (conf.isEnableWmailBlock()) startScanner(conf.getDirWmailBlock(), wmailBlockQueue);
		if (conf.isEnableWmail()) startScanner(conf.getDirWmail(), wmailQueue);
		log.info("START_SCAN | LOAD END\n");
	}

	private void startScanner(final String dir, final PriorityBlockingQueue<ScanData> queue) {
		if (Common.isEmpty(dir)) return;

		int waitingSec = conf.getScanDirectoryScanningWaitingSec();
		String dataPath = conf.getDataPath();
		int split = conf.getDecoderSplitDir();
		int fileWaitTime = conf.getInterval();

		ExecutorService executor = Executors.newFixedThreadPool(2, new NamedThreadFactory(new File(dir).getName()));
		executor.execute(new FileScanner(dir, queue, run, waitingSec, dataPath, split, fileWaitTime, conf.getNokRoot()));
		executor.shutdown();

//		WatchServiceScanner scanner = WatchServiceScanner.ofDefault(dir, queue, run, scannerCount, false);
//		ExecutorService es = Executors.newSingleThreadExecutor();
//		es.submit(scanner);

		log.info("START_SCAN | {}", dir);
	}

	private void startWorker(final List<AbstractWorker> workers) throws Exception {
		if (conf.isEnableWmailBlock()) startWorker(workers, wmailBlockQueue, conf.getWorkerSizeWmailBlock(), MSGWorker.class, "BLOCK");
		if (conf.isEnableWmail()) startWorker(workers, wmailQueue, conf.getWorkerSizeWmail(), MSGWorker.class, "WMAIL");
		log.info("START_WORKER | LOAD END\n");
	}

	private void startWorker(final List<AbstractWorker> workers, final PriorityBlockingQueue<ScanData> queue, int workerSize, Class<? extends AbstractWorker> workerClass, final String name) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(workerSize, new NamedThreadFactory(name));
		for (int i = 0; i < workerSize; i++) {
			AbstractWorker worker = workerClass.getDeclaredConstructor(ApplicationContext.class, PriorityBlockingQueue.class, AtomicBoolean.class).newInstance(context, queue, run);
			workers.add(worker);
			executor.execute(worker);
		}
		log.info("START_WORKER | {}", workerClass.getName());
		executor.shutdown();
	}
}
