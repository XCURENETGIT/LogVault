package com.xcurenet.logvault.module.scanner;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.LogVaultApplication;
import com.xcurenet.logvault.module.ScanData;
import lombok.Builder;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Java WatchService 기반 디렉토리 감시 스캐너 (운영용)
 * - 등록 순서 고정: registerAll → seedOnce → startPeriodicReconcile
 * - 신규 디렉토리: registerAll 직후 seedDirectory로 레이스 커버
 * - OVERFLOW: 얕은 깊이 재귀 보정(rescanRecursive)
 * - 디바운스/코얼레싱 + 크기 안정화(or .part→ATOMIC_MOVE)로 '쓰기 완료' 판정
 */
@Log4j2
public class WatchServiceScanner implements Runnable, AutoCloseable {

	private final Path startDir;
	private final PriorityBlockingQueue<ScanData> scanQueue;
	private final AtomicBoolean run;
	private final AtomicInteger scannerCount;

	private final Duration pollTimeout;
	private final Duration sizeStableWait;
	private final Duration debounceWindow;
	private final boolean useAtomicRenamePattern; // true면 .part→rename 패턴 가정(권장)

	private final String tempSuffix; // 예: ".part"
	private final long deleteTmpDelayMs;

	private WatchService watcher;
	private final Map<WatchKey, Path> keyToDir = new ConcurrentHashMap<>();

	// 디바운스: 파일 경로 → 마지막 이벤트 시간
	private final ConcurrentHashMap<Path, Long> debounceMap = new ConcurrentHashMap<>();

	// 스케줄러 (크기 안정화 검사/주기적 리컨실/GC)
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
			Math.max(2, Runtime.getRuntime().availableProcessors() / 4),
			r -> {
				Thread t = new Thread(r, "watchscanner-scheduler");
				t.setDaemon(true);
				return t;
			}
	);

	@Builder
	public WatchServiceScanner(String dir,
	                           PriorityBlockingQueue<ScanData> scanQueue,
	                           AtomicBoolean run,
	                           AtomicInteger scannerCount,
	                           Duration pollTimeout,
	                           Duration sizeStableWait,
	                           Duration debounceWindow,
	                           boolean useAtomicRenamePattern,
	                           String tempSuffix,
	                           Duration deleteTmpDelay) {
		this.startDir = Paths.get(Objects.requireNonNull(dir, "dir"));
		this.scanQueue = Objects.requireNonNull(scanQueue, "scanQueue");
		this.run = Objects.requireNonNull(run, "run");
		this.scannerCount = (scannerCount != null) ? scannerCount : new AtomicInteger();
		this.pollTimeout = (pollTimeout != null) ? pollTimeout : Duration.ofMillis(1000);
		this.sizeStableWait = (sizeStableWait != null) ? sizeStableWait : Duration.ofMillis(300);
		this.debounceWindow = (debounceWindow != null) ? debounceWindow : Duration.ofMillis(200);
		this.useAtomicRenamePattern = useAtomicRenamePattern;
		this.tempSuffix = (tempSuffix != null) ? tempSuffix : ".part";
		this.deleteTmpDelayMs = (deleteTmpDelay != null) ? deleteTmpDelay.toMillis() : 24L * 60 * 60 * 1000;
	}

	@Override
	public void run() {
		final String tname = (startDir.getFileName() != null ? startDir.getFileName().toString() : "root") + "_watch";
		Thread.currentThread().setName(tname);
		MDC.put("scanner", tname);
		try (WatchService ws = FileSystems.getDefault().newWatchService()) {
			this.watcher = ws;

			// (A) 재귀 등록을 먼저 (초기 레이스 방지)
			int count = registerAll(startDir);
			log.info("WatchServiceScanner registered {} directories under {}", count, startDir);

			// (B) 등록 이후 '씨딩' 한 번 더(등록~씨딩 사이 레이스 커버)
			seedOnce();

			// (C) 안전망: 주기적 얕은 깊이 리컨실 & 디바운스 맵 GC
			startPeriodicReconcile();

			// (D) 이벤트 루프
			while (run.get()) {
				WatchKey key = ws.poll(pollTimeout.toMillis(), TimeUnit.MILLISECONDS);
				if (key == null) continue;

				Path dir = keyToDir.get(key);
				if (dir == null) {
					safeReset(key);
					continue;
				}

				for (WatchEvent<?> ev : key.pollEvents()) {
					WatchEvent.Kind<?> kind = ev.kind();

					if (kind == OVERFLOW) {
						log.warn("OVERFLOW at {}", dir);
						rescanRecursive(dir, 3); // 얕은 깊이(2~4) 권장
						continue;
					}

					@SuppressWarnings("unchecked")
					WatchEvent<Path> pe = (WatchEvent<Path>) ev;
					Path name = pe.context();
					Path child = dir.resolve(name);

					if (kind == ENTRY_CREATE) {
						if (Files.isDirectory(child)) {
							// 신규 디렉토리: 즉시 등록 & 즉시 씨딩 (레이스 창 제거)
							safeRegisterAll(child);
							seedDirectory(child);
						} else {
							onFileEvent(child);
						}
					} else if (kind == ENTRY_MODIFY) {
						onFileEvent(child);
					} // ENTRY_DELETE는 필요 시 훅 추가
				}

				if (!safeReset(key)) {
					keyToDir.remove(key);
				}
			}
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		} catch (Throwable t) {
			log.error("Watch loop error", t);
		} finally {
			closeQuietly();
			MDC.remove("scanner");
		}
	}

	/**
	 * 파일 이벤트 처리 (디바운스 → 준비 판정 → 큐 투입 or 정리)
	 */
	private void onFileEvent(Path p) {
		try {
			if (!Files.exists(p) || Files.isDirectory(p)) return;
			if (isHidden(p)) return;

			// 디바운스: 최근 이벤트 창 내 중복 억제
			long now = System.nanoTime();
			Long last = debounceMap.put(p, now);
			if (last != null && nanosToDuration(now - last).compareTo(debounceWindow) < 0) {
				return;
			}

			// .part → 최종본 rename 패턴이면, 작성중(.part)은 스킵
			if (useAtomicRenamePattern && p.getFileName().toString().endsWith(tempSuffix)) {
				return;
			}

			File f = p.toFile();
			if (!Common.filePermission(f)) {
				maybeDeleteBadTemp(f);
				return;
			}

			if (useAtomicRenamePattern) {
				// 최종본은 ATOMIC_MOVE로 이미 완성 → 즉시 큐 투입
				addQueue(new ScanData(f, scannerCount));
			} else {
				// 크기 안정화 전략 (디바운스 창 뒤로 지연 후 확인)
				scheduler.schedule(() -> {
					try {
						if (!Files.exists(p) || Files.isDirectory(p)) return;
						File f2 = p.toFile();
						long s1 = f2.length();
						Common.sleep(sizeStableWait.toMillis());
						long s2 = f2.length();
						if (s1 > 0 && s1 == s2) {
							addQueue(new ScanData(f2, scannerCount));
						}
					} catch (Throwable t) {
						log.error("size-stable check error: {}", p, t);
					}
				}, debounceWindow.toMillis(), TimeUnit.MILLISECONDS);
			}
		} catch (Throwable t) {
			log.error("onFileEvent error: {}", p, t);
		}
	}

	private void addQueue(final ScanData data) {
		while (run.get() && scanQueue.size() >= LogVaultApplication.QUEUE_CAPACITY) {
			Common.sleep(150);
		}
		if (!run.get()) return;
		scanQueue.add(data);
		data.incrementCount();
		log.debug("Enqueued: {}", data.getFilePath());
	}

	/**
	 * 초기 1회 가벼운 스캔(등록 이후 실행)
	 */
	private void seedOnce() throws IOException {
		if (!Files.isDirectory(startDir)) return;
		Files.walkFileTree(startDir, new SimpleFileVisitor<>() {
			@NotNull
			@Override
			public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
				try {
					if (!Files.isRegularFile(file)) return FileVisitResult.CONTINUE;
					File f = file.toFile();
					if (isHidden(file)) return FileVisitResult.CONTINUE;
					if (!Common.filePermission(f)) {
						maybeDeleteBadTemp(f);
						return FileVisitResult.CONTINUE;
					}

					if (useAtomicRenamePattern) {
						if (!f.getName().endsWith(tempSuffix) && f.length() > 0) {
							addQueue(new ScanData(f, scannerCount));
						}
					} else {
						if (f.length() > 0 && isSizeStable(f, sizeStableWait)) {
							addQueue(new ScanData(f, scannerCount));
						}
					}
				} catch (Throwable t) {
					log.error("seedOnce error: {}", file, t);
					Common.removeAllPermissions(file.toFile());
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * 신규 디렉토리 생성 직후, 해당 폴더만 즉시 씨딩 (레이스 보완)
	 */
	private void seedDirectory(Path dir) {
		File[] list = dir.toFile().listFiles();
		if (list == null) return;
		for (File f : list) {
			if (f.isDirectory()) continue;
			if (f.isHidden()) continue;
			if (!Common.filePermission(f)) {
				maybeDeleteBadTemp(f);
				continue;
			}

			if (useAtomicRenamePattern) {
				if (!f.getName().endsWith(tempSuffix) && f.length() > 0) {
					try {
						addQueue(new ScanData(f, scannerCount));
					} catch (Exception e) {
						log.error("seedDirectory enqueue error: {}", f.getAbsolutePath(), e);
						Common.removeAllPermissions(f);
					}
				}
			} else {
				if (f.length() > 0 && isSizeStable(f, sizeStableWait)) {
					try {
						addQueue(new ScanData(f, scannerCount));
					} catch (Exception e) {
						log.error("seedDirectory enqueue error: {}", f.getAbsolutePath(), e);
						Common.removeAllPermissions(f);
					}
				}
			}
		}
	}

	/**
	 * OVERFLOW 등 보정용: 얕은 깊이 재귀 스캔
	 */
	private void rescanRecursive(Path root, int maxDepth) {
		try {
			if (!Files.exists(root)) return;
			if (!Files.isDirectory(root)) {
				// 파일이면 상위 폴더만 보정
				rescanDirectory(root.getParent() != null ? root.getParent() : root);
				return;
			}
			Files.walk(root, Math.max(1, maxDepth))
					.filter(Files::isRegularFile)
					.forEach(p -> {
						File f = p.toFile();
						if (f.isHidden()) return;
						if (!Common.filePermission(f)) {
							maybeDeleteBadTemp(f);
							return;
						}

						if (useAtomicRenamePattern) {
							if (!f.getName().endsWith(tempSuffix) && f.length() > 0) {
								try {
									addQueue(new ScanData(f, scannerCount));
								} catch (Exception e) {
									log.error("rescanRecursive enqueue error: {}", f.getAbsolutePath(), e);
									Common.removeAllPermissions(f);
								}
							}
						} else {
							if (f.length() > 0 && isSizeStable(f, sizeStableWait)) {
								try {
									addQueue(new ScanData(f, scannerCount));
								} catch (Exception e) {
									log.error("rescanRecursive enqueue error: {}", f.getAbsolutePath(), e);
									Common.removeAllPermissions(f);
								}
							}
						}
					});
		} catch (IOException e) {
			log.warn("rescanRecursive failed at {}: {}", root, e.toString());
		}
	}

	/**
	 * 디렉토리 한 단계 보정(파일만)
	 */
	private void rescanDirectory(Path dir) {
		File[] list = dir != null ? dir.toFile().listFiles() : null;
		if (list == null) return;
		for (File f : list) {
			if (f.isDirectory()) continue;
			if (f.isHidden()) continue;
			if (!Common.filePermission(f)) {
				maybeDeleteBadTemp(f);
				continue;
			}

			if (useAtomicRenamePattern) {
				if (!f.getName().endsWith(tempSuffix) && f.length() > 0) {
					try {
						addQueue(new ScanData(f, scannerCount));
					} catch (Exception e) {
						log.error("rescan enqueue error: {}", f.getAbsolutePath(), e);
						Common.removeAllPermissions(f);
					}
				}
			} else {
				if (f.length() > 0 && isSizeStable(f, sizeStableWait)) {
					try {
						addQueue(new ScanData(f, scannerCount));
					} catch (Exception e) {
						log.error("rescan enqueue error: {}", f.getAbsolutePath(), e);
						Common.removeAllPermissions(f);
					}
				}
			}
		}
	}

	/**
	 * 재귀 등록
	 */
	private int registerAll(Path root) throws IOException {
		if (!Files.isDirectory(root)) return 0;
		final int[] added = {0};
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@NotNull
			@Override
			public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
				WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
				keyToDir.put(key, dir);
				added[0]++;
				return FileVisitResult.CONTINUE;
			}
		});
		return added[0];
	}

	private void safeRegisterAll(Path childDir) {
		try {
			int n = registerAll(childDir);
			log.debug("Registered {} new directories under {}", n, childDir);
		} catch (IOException e) {
			log.warn("registerAll failed: {}", childDir, e);
		}
	}

	private boolean safeReset(WatchKey key) {
		try {
			return key.reset();
		} catch (Throwable t) {
			log.warn("WatchKey reset failed: {}", key, t);
			return false;
		}
	}

	private boolean isSizeStable(File f, Duration wait) {
		long s1 = f.length();
		Common.sleep(wait.toMillis());
		long s2 = f.length();
		return (s1 > 0 && s1 == s2);
	}

	private boolean isHidden(Path p) {
		try {
			return Files.isHidden(p) || p.getFileName().toString().startsWith(".");
		} catch (IOException e) {
			return p.getFileName().toString().startsWith(".");
		}
	}

	private void maybeDeleteBadTemp(File file) {
		try {
			if (Common.diffTime(file.lastModified()) > deleteTmpDelayMs) {
				log.info("Time delay and not permission: {}", file.getAbsolutePath());
				if (!Common.isWindow()) {
					Files.deleteIfExists(file.toPath());
				}
			}
		} catch (Throwable t) {
			log.error("delete temp failed: {}", file.getAbsolutePath(), t);
		}
	}

	private static Duration nanosToDuration(long nanos) {
		return Duration.ofNanos(nanos);
	}

	/**
	 * 주기적 얕은 깊이 리컨실 + 디바운스 맵 GC
	 */
	private void startPeriodicReconcile() {
		scheduler.scheduleWithFixedDelay(() -> {
			try {
				if (!run.get()) return;
				rescanRecursive(startDir, 2); // 비용 낮은 안전망
				gcDebounceMap();
			} catch (Throwable t) {
				log.warn("periodic reconcile failed: {}", t.toString());
			}
		}, 2, 2, TimeUnit.MINUTES);
	}

	/**
	 * 오래된 디바운스 엔트리 정리(누수 방지)
	 */
	private void gcDebounceMap() {
		long now = System.nanoTime();
		long keepNanos = debounceWindow.multipliedBy(10).toNanos();
		debounceMap.entrySet().removeIf(e -> now - e.getValue() > keepNanos);
	}

	@Override
	public void close() {
		run.set(false);
		closeQuietly();
	}

	private void closeQuietly() {
		try {
			if (watcher != null) watcher.close();
		} catch (IOException ignored) {
		}
		scheduler.shutdownNow();
	}

	// ====== 빌더 헬퍼 ======
	public static WatchServiceScanner ofDefault(String dir, PriorityBlockingQueue<ScanData> scanQueue, AtomicBoolean runFlag, AtomicInteger scannerCount, boolean useAtomicRenamePattern) {
		return WatchServiceScanner.builder()
				.dir(dir)
				.scanQueue(scanQueue)
				.run(runFlag)
				.scannerCount(scannerCount)
				.pollTimeout(Duration.ofMillis(1000))
				.sizeStableWait(Duration.ofMillis(300))
				.debounceWindow(Duration.ofMillis(200))
				.useAtomicRenamePattern(useAtomicRenamePattern)
				.tempSuffix(".part")
				.deleteTmpDelay(Duration.ofDays(1))
				.build();
	}
}
