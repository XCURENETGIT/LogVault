package com.xcurenet.logvault.module.scanner;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.LogVaultApplication;
import com.xcurenet.logvault.module.ScanData;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Log4j2
public class FileScanner implements Runnable {
	private static final int MAX_DEPTH = 2; // DirectoryWalker 의 최대 탐색 깊이
	private static final long QUEUE_OFFER_WAIT_MS = 500L; // 큐 적재 재시도 대기(밀리초)

	private static final ConcurrentHashMap<String, Boolean> PROCESSING_SET = new ConcurrentHashMap<>(); // 중복 처리 방지를 위한 처리 중인 파일의 임시 저장

	private final Path startDirectory;
	private final PriorityBlockingQueue<ScanData> queue;
	private final AtomicBoolean run;
	private final AtomicInteger scannerCount;
	private final int scanningWaitingSec;

	public FileScanner(final String dir, final PriorityBlockingQueue<ScanData> queue, final AtomicBoolean run, final int scanningWaitingSec) {
		this.startDirectory = Paths.get(Objects.requireNonNull(dir, "dir must not be null"));
		this.queue = Objects.requireNonNull(queue, "queue must not be null");
		this.run = Objects.requireNonNull(run, "run must not be null");
		this.scannerCount = new AtomicInteger();
		this.scanningWaitingSec = Math.max(1, scanningWaitingSec) * 1000;

		final String threadName = startDirectory.getFileName() + "_scan";
		Thread.currentThread().setName(threadName);
	}


	@Override
	public void run() {
		ensureStartDirectory();
		while (isRunning()) {
			try {
				if (!queue.isEmpty() || !PROCESSING_SET.isEmpty()) { // 아직 Worker에서 처리 중이라면 대기
					log.debug("[Scanner] Queue is full. Waiting...");
					Common.sleep(300);
					continue;
				}

				scan(startDirectory);
				Common.sleep(scanningWaitingSec);
			} catch (Exception e) {
				log.error("Scanner Error: ", e);
			}
		}
	}

	private void scan(Path rootDir) {
		try (Stream<Path> stream = Files.walk(rootDir, MAX_DEPTH)) {
			stream.filter(this::isValidCandidate) // 파일 크기가 0이거나, 권한이 755가 아니면 SKIP
					.filter(path -> PROCESSING_SET.putIfAbsent(path.toAbsolutePath().toString(), true) == null) //Worker에서 처리 중인 파일이면 SKIP
					.limit(LogVaultApplication.QUEUE_CAPACITY) // 큐 크기만큼만 파일 스캔
					.forEach(path -> {

						ScanData data = null;
						try {
							data = new ScanData(path, scannerCount);
						} catch (Exception e) {
							log.error("SCANNER | File parsing error. Set no-permission to avoid reprocessing: {}", path, e);
							Common.removeAllPermissions(path.toFile()); // 파싱 오류는 중복 처리 불가함.
							removeFromQueue(path.toAbsolutePath().toString());  // 에러 발생시에도 임시 캐시는 초기화
						}

						try {
							if (data != null) addQueue(data);
						} catch (Exception e) {
							log.error("SCANNER | queue add fail: {}", path, e);
							removeFromQueue(path.toAbsolutePath().toString());  // 에러 발생 시 임시 캐싱 영역의 파일 경로를 제거해줌. 재 처리를 해야하므로
						}
					});
		} catch (IOException e) {
			log.error("SCANNER | Error : {}", rootDir, e);
		}
	}

	/**
	 * 스캐닝한 파일의 유효성
	 *
	 * @param path 파일 경로
	 * @return 유효 여부
	 */
	private boolean isValidCandidate(Path path) {
		try {
			if (!Files.isRegularFile(path)) return false; // 파일이 아니거나
			if (Files.size(path) == 0) return false; // 파일이 0이거나
			if (Files.isHidden(path)) return false; // 숨김 파일 이거나
			if (!Common.filePermission(path.toFile())) return false; // 0755 권한이 아니거나
		} catch (Exception e) {
			log.warn("SCANNER | Error : {}", path, e);
			return false;
		}
		return true;
	}


	private void addQueue(final ScanData data) {
		if (!isRunning()) return;

		while (isRunning()) {
			if (queue.offer(data, QUEUE_OFFER_WAIT_MS, TimeUnit.MILLISECONDS)) {
				data.incrementCount();
				log.debug("ENQ | {}", data.getFilePath());
				return;
			}
			log.warn("ENQ-TIMEOUT | Retrying... file={}", data.getFilePath());
		}
	}

	/**
	 * 스캐너 동작 전 root 디렉토리의 검증
	 * root 디렉토리가 없다면 생성.
	 */
	private void ensureStartDirectory() {
		try {
			if (Files.notExists(startDirectory)) {
				Files.createDirectories(startDirectory);
				log.info("SCANNER | Created start directory: {}", startDirectory.toAbsolutePath());
				return;
			}
			if (!Files.isDirectory(startDirectory)) {
				throw new IllegalStateException("Start path is not a directory: " + startDirectory);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to prepare start directory: " + startDirectory, e);
		}
	}

	private boolean isRunning() {
		return run.get() && !Thread.currentThread().isInterrupted();
	}

	/**
	 * Worker에서 처리 완료 후 큐에 내용을 지워야 한다.
	 *
	 * @param absPath 파일 경로
	 */
	public static void removeFromQueue(final String absPath) {
		PROCESSING_SET.remove(absPath);
	}
}