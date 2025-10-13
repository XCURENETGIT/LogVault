package com.xcurenet.logvault;

import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.common.utils.FileTimeComparator;
import com.xcurenet.common.utils.NamedThreadFactory;
import com.xcurenet.crypto.Crypto;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.scanner.Scanner;
import com.xcurenet.logvault.module.worker.AbstractLogVaultWorker;
import com.xcurenet.logvault.module.worker.MSGWorker;
import jakarta.annotation.PostConstruct;
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

import java.io.Console;
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

	@PostConstruct
	private void loadEncryptKey() {
		if (conf.isEncryptEnable()) {
			if (!new File(conf.getEncryptKeyFile()).exists()) {
				log.warn("The file encryption setting is enabled, but no key file is available.");
				if (!CommonUtil.isWindow()) System.exit(1);

				//아래 key 생성은 실전에서는 필요없음. (모듈 실행 전 키 파일이 필요함)
				if (!makeKey()) {
					log.error("Failed to generate the encryption key file: {}", conf.getEncryptKey());
					loadEncryptKey();
				}
			}

			final String key = CommonUtil.toHexString(Crypto.loadKeyFile(conf.getEncryptKeyFile()));
			if (CommonUtil.isNotEmpty(key)) {
				log.info("[LOAD_ENCRYPT] {} | {}", conf.getEncryptKeyFile(), conf.getEncryptCipher());
				conf.setEncryptKey(key);
			} else {
				log.error("Invalid Key File: {}", conf.getEncryptKey());
				System.exit(1);
			}
		}
	}

	private boolean makeKey() {
		return Crypto.makeKeyFile(conf.getEncryptKeyFile(), inputPassword());
	}

	private String inputPassword() {
		Console console = System.console();
		String password;
		if (console != null) {
			while (true) {
				char[] firstInput = console.readPassword("For security, the message body and attachments will be encrypted. Please set a password: ");
				char[] confirmInput = console.readPassword("Please confirm your password: ");

				String pass1 = new String(firstInput);
				String pass2 = new String(confirmInput);
				if (pass1.equals(pass2)) {
					password = pass1;
					break;
				} else {
					System.out.println("❌ Passwords do not match. Please try again.\n");
				}
			}
		} else {
			java.util.Scanner scanner = new java.util.Scanner(System.in);
			while (true) {
				System.out.print("For security, the message body and attachments will be encrypted. Please set a password: ");
				String pass1 = scanner.nextLine();

				System.out.print("Please confirm your password: ");
				String pass2 = scanner.nextLine();
				if (pass1.equals(pass2)) {
					password = pass1;
					break;
				} else {
					System.out.println("❌ Passwords do not match. Please try again.\n");
				}
			}
		}
		System.out.println("✅ Password confirmed successfully!");
		return password;
	}
}