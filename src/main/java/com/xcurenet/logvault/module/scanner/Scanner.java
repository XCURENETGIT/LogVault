package com.xcurenet.logvault.module.scanner;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.LogVaultApplication;
import com.xcurenet.logvault.module.ScanData;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.DirectoryWalker;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 시작 디렉터리를 순회하며 스캔 대기열에 파일을 적재한다.
 * - 권한(7xx) & 비숨김 & 사이즈>0 파일만 큐에 적재
 * - 이름 파싱 실패 시 권한 제거(재처리 방지)
 * - 24시간 지난 "잘못된 권한" 파일은 삭제
 * - 24시간 지난 빈 디렉터리는 재생성(최상위 시작 디렉터리는 제외)
 * <p>
 * 구현 포인트
 * 1) run 플래그와 인터럽트를 모두 존중한 안전한 중단
 * 2) 큐가 가득 찼을 때 offer(timeout) 기반 백프레셔
 * 3) 적응형 백오프: 새 파일 있으면 즉시 재스캔, 없으면 짧게 쉬고 점증
 * 4) 최소 변경으로 중복 회피: ENQUEUED(ConcurrentHashMap) 기반 중복 적재 방지 + 자동 스윕
 */
@Log4j2
public final class Scanner extends DirectoryWalker<File> implements Runnable {

	// ==== 설정 상수 ====
	/**
	 * 파일/폴더 정리 기준 지연(기본: 24시간)
	 */
	private static final Duration DEFAULT_DELETE_TMP_DELAY = Duration.ofDays(1);
	/**
	 * 큐 적재 재시도 대기(밀리초)
	 */
	private static final long QUEUE_OFFER_WAIT_MS = 500L;
	/**
	 * 스캔 도중 취소 체크 간격(밀리초)
	 */
	private static final long CANCEL_CHECK_SLEEP_MS = 250L;
	/**
	 * DirectoryWalker 의 최대 탐색 깊이(기존: 2)
	 */
	private static final int MAX_DEPTH = 2;

	// ==== 중복 방지 레지스트리 ====
	/**
	 * 현재 ENQUEUE/처리 중인 파일의 절대경로 키 보관
	 * 값(Long)은 마지막 확인 시각(스윕용, stale 청소에 사용)
	 */
	private static final ConcurrentHashMap<String, Long> ENQUEUED = new ConcurrentHashMap<>();

	// ==== 주입 필드 ====
	private final File startDirectory;
	private final PriorityBlockingQueue<ScanData> scanQueue;
	private final AtomicBoolean run;
	private final AtomicInteger scannerCount;
	private final int maxBackoffSec;               // 기존 scanningWaitingSec → '최대 백오프' 로 의미 재해석
	private final Duration deleteTmpDelay;

	// ==== 적응형 백오프 상태 ====
	private volatile int enqueuedThisRound = 0;     // 이번 라운드에서 enqueue된 수
	private long currentBackoffMs = 0L;             // 현재 백오프(ms)

	// ==== 생성자 ====
	public Scanner(final String dir, final PriorityBlockingQueue<ScanData> scanQueue, final AtomicBoolean run, final int scanningWaitingSec) {
		this(dir, scanQueue, run, scanningWaitingSec, DEFAULT_DELETE_TMP_DELAY);
	}

	public Scanner(final String dir, final PriorityBlockingQueue<ScanData> scanQueue, final AtomicBoolean run, final int scanningWaitingSec, final Duration deleteTmpDelay) {
		super(null, MAX_DEPTH);
		this.startDirectory = new File(Objects.requireNonNull(dir, "dir must not be null"));
		this.scanQueue = Objects.requireNonNull(scanQueue, "scanQueue must not be null");
		this.run = Objects.requireNonNull(run, "run must not be null");
		this.scannerCount = new AtomicInteger();
		this.maxBackoffSec = Math.max(0, scanningWaitingSec);
		this.deleteTmpDelay = Objects.requireNonNullElse(deleteTmpDelay, DEFAULT_DELETE_TMP_DELAY);
		this.currentBackoffMs = 0L;
	}

	// ==== 실행 루프 ====
	@Override
	public void run() {
		final String threadName = startDirectory.getName() + "_scan";
		Thread.currentThread().setName(threadName);

		ensureStartDirectory();

		final long STEP_BACKOFF_MS = 200L;                   // 못 찾으면 200ms씩 증가
		final long MAX_BACKOFF_MS = maxBackoffSec * 1000L;   // 상한 (설정값)

		while (isRunning()) {
			// === 추가: 워커가 파일을 이동/삭제하면 ENQUEUED에서 자동 제거 ===
			sweepEnqueued();

			enqueuedThisRound = 0;
			try {
				walk(startDirectory, null);
			} catch (IOException e) {
				log.error("SCANNER | Error while scanning directory: {}", startDirectory, e);
			}

			if (!isRunning()) break;

			if (enqueuedThisRound > 0) {
				// 새 작업을 넣었다 → 즉시 다음 라운드 (대기 없음)
				if (currentBackoffMs != 0) {
					log.debug("SCANNER | found={} -> reset backoff (was {} ms)", enqueuedThisRound, currentBackoffMs);
				}
				currentBackoffMs = 0;
				continue;
			}

			// 새 파일이 없었음 → 백오프 점증
			currentBackoffMs = Math.min(currentBackoffMs == 0 ? STEP_BACKOFF_MS : currentBackoffMs + STEP_BACKOFF_MS, MAX_BACKOFF_MS);
			if (currentBackoffMs > 0) {
				log.debug("IDLE | no new files. backoff={} ms | queueSize={} | scannerCount={}", currentBackoffMs, scanQueue.size(), scannerCount.get());
				sleepRespectingCancel(currentBackoffMs);
			}
		}
		log.info("SCANNER | Scanner stopped: dir={}", startDirectory);
	}

	// ==== DirectoryWalker 콜백 ====
	@Override
	protected void handleFile(final File file, final int depth, final Collection<File> results) {
		if (!isRunning()) return;

		if (isProcessable(file)) {
			try {
				addQueue(new ScanData(file, scannerCount)); // 성공 시 내부에서 enqueuedThisRound++
			} catch (Exception e) {
				log.error("SCANNER | File parsing error. Set no-permission to avoid reprocessing: {}", file.getAbsolutePath(), e);
				// 파싱 실패 → 재처리 방지
				Common.removeAllPermissions(file);
			}
			return;
		}

		// 처리 불가 파일 정리: 24시간 경과 & Windows 가 아닐 때만 삭제 시도
		if (elapsedSince(file.lastModified()) > deleteTmpDelay.toMillis()) {
			log.info("SCANNER | Delete stale file without proper permission: {}", file.getAbsolutePath());
			if (!Common.isWindow()) {
				try {
					Files.deleteIfExists(file.toPath());
				} catch (IOException e) {
					log.error("SCANNER | Error while deleting file: {}", file.getAbsolutePath(), e);
				}
			}
		}
	}

	@Override
	protected boolean handleDirectory(final File directory, final int depth, final Collection<File> results) {
		if (!isRunning()) return false;
		return reconcileDirectory(directory);
	}

	@Override
	protected boolean handleIsCancelled(final File file, final int depth, final Collection<File> results) {
		return !isRunning();
	}

	@Override
	protected void handleCancelled(final File startDirectory, final Collection<File> results, final CancelException cancel) {
		// no-op
	}

	// ==== 내부 로직 ====

	private boolean isRunning() {
		return run.get() && !Thread.currentThread().isInterrupted();
	}

	private void ensureStartDirectory() {
		try {
			if (!startDirectory.exists()) {
				FileUtils.forceMkdir(startDirectory);
				log.info("SCANNER | Created start directory: {}", startDirectory.getAbsolutePath());
			} else if (!startDirectory.isDirectory()) {
				throw new IllegalStateException("Start path is not a directory: " + startDirectory);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to prepare start directory: " + startDirectory, e);
		}
	}

	private boolean isProcessable(final File file) {
		return file != null && file.isFile() && file.length() > 0 && !file.isHidden() && Common.filePermission(file); // 7xx
	}

	private long elapsedSince(final long lastModifiedMillis) {
		return System.currentTimeMillis() - lastModifiedMillis;
	}

	/**
	 * 큐가 가득 찼을 때 offer(timeout)으로 대기하며 백프레셔를 준다.
	 * 외부 consumer 가 완료하면 ScanData 내부의 scannerCount 가 감소할 것으로 기대.
	 * 성공 시 enqueuedThisRound++ 로 이번 라운드 즉시 재스캔 트리거.
	 * + 중복 적재 방지: ENQUEUED.putIfAbsent(key) 가드
	 */
	private void addQueue(final ScanData data) throws InterruptedException {
		if (!isRunning()) return;

		final String key = safeAbsPath(new File(data.getFilePath())); // 절대경로 키
		// === 중복 방지: 이미 ENQUEUED면 스킵 ===
		log.debug("ENQUEUED | {}", ENQUEUED.size());
		if (ENQUEUED.putIfAbsent(key, System.currentTimeMillis()) != null) {
			log.debug("DEDUP | skip already enqueued or processing: {}", key);
			return;
		}

		try {
			// 큐 용량을 넘겼을 경우: offer(timeout)으로 기다렸다가 재시도
			while (isRunning() && scanQueue.size() >= LogVaultApplication.QUEUE_CAPACITY) {
				log.debug("BACKPRESSURE | queue full (cap={}), waiting {}ms...", LogVaultApplication.QUEUE_CAPACITY, QUEUE_OFFER_WAIT_MS);
				sleepRespectingCancel(QUEUE_OFFER_WAIT_MS);
			}
			if (!isRunning()) return;

			// 남은 여유가 있어도 race 로 실패할 수 있으니 루프로 offer(timeout)
			while (isRunning()) {
				boolean offered = scanQueue.offer(data, QUEUE_OFFER_WAIT_MS, TimeUnit.MILLISECONDS);
				if (offered) {
					data.incrementCount();  // 소비자 완료 시 decrement 예상
					enqueuedThisRound++;    // 이번 라운드에 적재됨 → 즉시 재스캔 유도
					log.debug("ENQ | {}", data.getFilePath());
					return;
				}
				log.warn("ENQ-TIMEOUT | Retrying... file={}", data.getFilePath());
			}
		} finally {
			// 여기서 즉시 remove 하지 않음:
			// - 정상 offer 성공 시: 워커가 파일을 이동/삭제 → 다음 라운드 sweepEnqueued()에서 자동 해제
			// - 스캐너 중단/예외 시: 실행 상태가 false면 제거해 재시도 가능하게 함
			if (!isRunning()) {
				ENQUEUED.remove(key);
			}
		}
	}

	/**
	 * 빈 디렉터리 정리 정책:
	 * - 시작 디렉터리는 절대 삭제하지 않음
	 * - 24시간 경과 & 하위에 파일 없음 → 삭제 후 재생성
	 */
	private boolean reconcileDirectory(final File directory) {
		final File[] listFiles = directory.listFiles();
		if (listFiles == null) return false; // 접근 불가/권한 문제 등

		if (listFiles.length > 0) return true; // 자식이 있으면 계속 탐색

		// 시작 디렉터리는 보호
		if (startDirectory.getAbsolutePath().equals(directory.getAbsolutePath())) return true;

		// 오래된 빈 디렉터리는 재생성
		if (elapsedSince(directory.lastModified()) > deleteTmpDelay.toMillis()) {
			log.debug("RECREATE | stale empty directory: {}", directory.getAbsolutePath());
			// delete()는 디렉터리가 비어있을 때만 true → 우리는 이미 빈 상태
			if (directory.delete()) {
				directory.deleteOnExit(); // 프로세스 종료 시 재확인 삭제
			}
			try {
				FileUtils.forceMkdir(directory);
			} catch (IOException e) {
				log.error("SCANNER | Error while creating directory: {}", directory, e);
			}
			return false; // 이 디렉터리는 더 이상 탐색 불필요
		}
		return true;
	}

	private void sleepRespectingCancel(final long millis) {
		if (millis <= 0) return;
		long remaining = millis;
		while (isRunning() && remaining > 0) {
			long step = Math.min(remaining, CANCEL_CHECK_SLEEP_MS);
			Common.sleep(step);
			remaining -= step;
		}
	}

	// ==== 추가 유틸 ====

	/**
	 * ENQUEUED 키(절대경로) 스윕: 파일이 사라졌거나(stale) 오래된 항목 제거
	 */
	private void sweepEnqueued() {
		final long now = System.currentTimeMillis();
		ENQUEUED.forEach((absPath, ts) -> {
			File f = new File(absPath);
			if (!f.exists()) {
				ENQUEUED.remove(absPath);
				log.debug("DEDUP | cleared (not exists): {}", absPath);
				return;
			}
			// 보수적 청소: 너무 오래된 항목은 한번 풀어주고 다시 잡히게 함
			if (now - ts > DEFAULT_DELETE_TMP_DELAY.toMillis()) {
				ENQUEUED.remove(absPath);
				log.debug("DEDUP | cleared (stale): {}", absPath);
			}
		});
	}

	private static String safeAbsPath(File f) {
		try {
			return f.getCanonicalPath();
		} catch (IOException e) {
			return f.getAbsolutePath();
		}
	}
}
