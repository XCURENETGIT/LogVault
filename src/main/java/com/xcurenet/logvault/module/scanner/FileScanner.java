package com.xcurenet.logvault.module.scanner;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.LogVaultApplication;
import com.xcurenet.logvault.module.ScanData;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
	private static final Set<String> TARGET_KEYS = Set.of("HDRFILE", "MSGFILE", "APPFILE");
	private static final ConcurrentHashMap<String, Boolean> PROCESSING_SET = new ConcurrentHashMap<>(); // 중복 처리 방지를 위한 처리 중인 파일의 임시 저장

	private final Path startDirectory;
	private final PriorityBlockingQueue<ScanData> queue;
	private final AtomicBoolean run;
	private final AtomicInteger scannerCount;
	private final int scanningWaitingSec;
	private final String dataPath;
	private final int split;
	private final int fileWaitTime;

	public FileScanner(final String dir, final PriorityBlockingQueue<ScanData> queue, final AtomicBoolean run, final int scanningWaitingSec, final String dataPath, final int split, final int fileWaitTime) {
		this.startDirectory = Paths.get(Objects.requireNonNull(dir, "dir must not be null"));
		this.queue = Objects.requireNonNull(queue, "queue must not be null");
		this.run = Objects.requireNonNull(run, "run must not be null");
		this.scannerCount = new AtomicInteger();
		this.scanningWaitingSec = Math.max(1, scanningWaitingSec) * 1000;
		this.dataPath = dataPath;
		this.split = split;
		this.fileWaitTime = fileWaitTime;

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

	private boolean isValidCandidate(Path path) {
		try {
			BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
			if (!attrs.isRegularFile() || attrs.size() <= 0) return false; //정규 파일이 아니거나, 사이즈가 0 이거나
			if (Files.isHidden(path)) return false; //숨김 파일
			if (!Common.filePermission(path.toFile())) return false; //0755 권한 검사
			return isFileValid(path, attrs.lastModifiedTime().toMillis());
		} catch (IOException | SecurityException e) {
			log.warn("SCANNER | Invalid file access: {} - {}", path, e.getMessage()); // 파일 접근 불가, 삭제됨, 권한 없음 등의 상황
			return false;
		}
	}

	/**
	 * Validates whether a given file meets certain criteria for processing.
	 *
	 * @param path         the path of the file to validate
	 * @param lastModified the last modified time of the file, in milliseconds
	 * @return true if the file is valid; false otherwise
	 */
	private boolean isFileValid(final Path path, final long lastModified) {
		try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
			Optional<Path> missingFile = lines.filter(line -> TARGET_KEYS.stream().anyMatch(line::contains))
					.map(this::getFieldValue).filter(Common::isNotEmpty)
					.map(val -> Paths.get(getPath(val))).filter(Files::notExists)
					.findFirst();

			if (missingFile.isPresent()) {
				long elapsed = System.currentTimeMillis() - lastModified;
				if (elapsed < fileWaitTime) return false; // 대기 시간이 남았다면 유효하지 않음(false)으로 처리
				log.info("NOTFOUND | {} | Referenced file missing: {} ({}s over)", path, missingFile.get(), fileWaitTime / 1000);
			}
			return true;
		} catch (IOException e) {
			log.warn("SCANNER | Error reading file: {}", path, e);
			return false;
		}
	}

	public String getPath(final String fileName) {
		return Common.makeFilepath(dataPath, Long.toString(Common.getSplitNum(fileName, split)), fileName);
	}

	private String getFieldValue(final String line) {
		String[] fields = line.split(":", 2);
		if (fields.length > 1) return fields[1].trim();
		return null;
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