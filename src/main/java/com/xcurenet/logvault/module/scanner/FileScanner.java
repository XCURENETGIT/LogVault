package com.xcurenet.logvault.module.scanner;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.ExFactory;
import com.xcurenet.logvault.LogVaultApplication;
import com.xcurenet.logvault.exception.ScanException;
import com.xcurenet.logvault.module.ScanData;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;
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
	private final String nokRoot;

	public FileScanner(final String dir, final PriorityBlockingQueue<ScanData> queue, final AtomicBoolean run, final int scanningWaitingSec, final String dataPath, final int split, final int fileWaitTime, final String nokRoot) {
		this.startDirectory = Paths.get(Objects.requireNonNull(dir, "dir must not be null"));
		this.queue = Objects.requireNonNull(queue, "queue must not be null");
		this.run = Objects.requireNonNull(run, "run must not be null");
		this.scannerCount = new AtomicInteger();
		this.scanningWaitingSec = Math.max(1, scanningWaitingSec) * 1000;
		this.dataPath = dataPath;
		this.split = split;
		this.fileWaitTime = fileWaitTime;
		this.nokRoot = nokRoot;

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
					.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".msg"))
					.filter(path -> PROCESSING_SET.putIfAbsent(path.toAbsolutePath().toString(), true) == null) //Worker에서 처리 중인 파일이면 SKIP
					.limit(LogVaultApplication.QUEUE_CAPACITY) // 큐 크기만큼만 파일 스캔
					.forEach(path -> {

						ScanData data = null;
						try {
							String name = path.getFileName().toString();
							validateDetail(name);

							data = new ScanData(path, scannerCount);
						} catch (ScanException e) {
							log.error("{} | FILE:{}", ErrorCode.SCANNER_FILE_INVALID.toString(), path, e);
							Common.moveNok(path, this.nokRoot, dataPath, split);
							removeFromQueue(path.toAbsolutePath().toString());  // 에러 발생시에도 임시 캐시는 초기화
						} catch (Exception e) {
							log.error("{} | FILE:{}", ErrorCode.SCANNER_SCAN_FAIL.toString(), path, e);
							Common.moveNok(path, this.nokRoot, dataPath, split);
							removeFromQueue(path.toAbsolutePath().toString());  // 에러 발생시에도 임시 캐시는 초기화
						}

						try {
							if (data != null) addQueue(data);
						} catch (Exception e) {
							log.error("{} | FILE:{}", ErrorCode.SCANNER_QUEUE_ADD_FAIL.toString(), path, e);
							removeFromQueue(path.toAbsolutePath().toString());  // 에러 발생 시 임시 캐싱 영역의 파일 경로를 제거해줌. 재 처리를 해야하므로
						}
					});
		} catch (IOException e) {
			log.error("{} | ROOTDIR:{}", ErrorCode.SCANNER_SCAN_FAIL.toString(), rootDir, e);
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
			log.warn("{} | {} | file={}", ErrorCode.SCANNER_FILE_READ_FAIL.toString(), path, e);
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
		CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
		try (BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(path), decoder))) {
			String line;
			while ((line = br.readLine()) != null) {
				for (String key : TARGET_KEYS) {
					if (line.contains(key)) {
						String val = getFieldValue(line);
						if (Common.isNotEmpty(val)) {
							Path ref = Paths.get(getPath(val));
							if (Files.notExists(ref)) {
								long elapsed = System.currentTimeMillis() - lastModified;
								if (elapsed < fileWaitTime) {
									log.warn("{} | MSGFILE:{} REFFILE:{}", ErrorCode.SCANNER_REF_FILE_MISSING.toString(), path, ref);
									return false;
								}
							}
						}
					}
				}
			}
			return true;
		} catch (Exception e) {
			log.warn("{} | FILE:{}", ErrorCode.SCANNER_FILE_READ_FAIL.toString(), path, e);
			Common.moveNok(path, this.nokRoot, dataPath, split);
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

	/**
	 * 상세 로직 검증 (Split 사용)
	 * 각 항목의 범위(Port 65535 이하 등)나 길이를 디테일하게 체크
	 *
	 * @throws ScanException 검증 실패 시 발생
	 */
	public static void validateDetail(String fileName) { // 반환 타입을 void로 변경
		if (fileName == null || fileName.isEmpty()) {
			throw ExFactory.ex(ScanException::new, ErrorCode.SCAN_NAME_INVALID, Map.of("info", "file name is null or empty"));
		}

		int lastDot = fileName.lastIndexOf('.');
		if (lastDot <= 0) {
			throw ExFactory.ex(ScanException::new, ErrorCode.SCAN_NAME_INVALID, Map.of("info", "invalid file name or extension is missing", "file", fileName));
		}
		String[] parts = fileName.substring(0, lastDot).split("-", 10);
		if (parts.length < 9) { // LVT-5002: 구성 요소 개수 불일치 (parts[8] 접근 보장)
			throw ExFactory.ex(ScanException::new, ErrorCode.SCAN_NAME_PART_COUNT, Map.of("count", parts.length + ", Expected: 9", "file", fileName));
		}

		try {
			if (!parts[0].startsWith("WMAIL") || parts[0].length() != 19) { // Part 0: WMAIL + yyyyMMddHHmmss (총 19자)
				throw ExFactory.ex(ScanException::new, ErrorCode.SCAN_NAME_HEADER_INVALID, Map.of("value", parts[0], "file", fileName));
			}
			if (isNoneNumeric(parts[0].substring(5))) {
				throw ExFactory.ex(ScanException::new, ErrorCode.SCAN_NAME_HEADER_INVALID, Map.of("value", parts[0] + " (time part is not numeric)", "file", fileName));
			}

			if (isNoneHex(parts[1])) { // LVT-5004: Source IP Hex 형식 오류
				throw ExFactory.ex(ScanException::new, ErrorCode.SCAN_NAME_HEX_INVALID, Map.of("value", parts[1] + " (Source IP)", "file", fileName));
			}
			if (isNoneHex(parts[2])) {// LVT-5004: Destination IP Hex 형식 오류
				throw ExFactory.ex(ScanException::new, ErrorCode.SCAN_NAME_HEX_INVALID, Map.of("value", parts[2] + " (Destination IP)", "file", fileName));
			}

			int srcPort = Integer.parseInt(parts[3]);
			int dstPort = Integer.parseInt(parts[4]);
			if (srcPort < 0 || srcPort > 65535) { // LVT-5005: Source Port 범위 오류
				throw ExFactory.ex(ScanException::new, ErrorCode.SCAN_NAME_PORT_RANGE, Map.of("value", parts[3] + " (Source Port)", "file", fileName));
			}
			if (dstPort < 0 || dstPort > 65535) {// LVT-5005: Destination Port 범위 오류
				throw ExFactory.ex(ScanException::new, ErrorCode.SCAN_NAME_PORT_RANGE, Map.of("value", parts[4] + " (Destination Port)", "file", fileName));
			}

			if (isNoneNumeric(parts[5])) { // LVT-5007: Seq1 숫자 형식 오류
				throw ExFactory.ex(ScanException::new, ErrorCode.SCAN_NAME_SEQ_INVALID, Map.of("value", parts[5] + " (Seq 1)", "file", fileName));
			}
			if (isNoneNumeric(parts[6])) { // LVT-5007: Seq2 숫자 형식 오류
				throw ExFactory.ex(ScanException::new, ErrorCode.SCAN_NAME_SEQ_INVALID, Map.of("value", parts[6] + " (Seq 2)", "file", fileName));
			}


			if (parts[7].isEmpty()) { // LVT-5008: Host1 공백 오류
				throw ExFactory.ex(ScanException::new, ErrorCode.SCAN_NAME_HOST_EMPTY, Map.of("field", "Host 1", "file", fileName));
			}
			if (parts[8].isEmpty()) { // LVT-5008: Host2 공백 오류
				throw ExFactory.ex(ScanException::new, ErrorCode.SCAN_NAME_HOST_EMPTY, Map.of("field", "Host 2", "file", fileName));
			}

		} catch (NumberFormatException e) {
			String invalidPort = "";
			try {
				Integer.parseInt(parts[3]);
				invalidPort = parts[4]; // Src Port는 정상이므로 Dst Port가 문제
			} catch (NumberFormatException ignored) {
				invalidPort = parts[3]; // Src Port가 문제
			}
			throw ExFactory.ex(ScanException::new, ErrorCode.SCAN_NAME_PORT_FORMAT, Map.of("value", invalidPort, "file", fileName));
		}
	}

	// 유틸: 숫자인지 확인
	private static boolean isNoneNumeric(String str) {
		return !str.matches("\\d+");
	}

	// 유틸: Hex 문자열인지 확인
	private static boolean isNoneHex(String str) {
		return !str.matches("[0-9a-fA-F]+");
	}
}
