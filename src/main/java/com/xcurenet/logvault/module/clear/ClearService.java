package com.xcurenet.logvault.module.clear;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Log4j2
@Service
@RequiredArgsConstructor
public class ClearService {
	protected final Config conf;

	public void clear(final ScanData data) {
		StopWatch sw = DateUtils.start();
		MSGData msg = data.getMsgData();

		int bodyDeleted = 0;
		int headerDeleted = 0;
		int attachDeleted = 0;
		int embeddedDeleted = 0;
		int tempDeleted = 0;
		int msgDeleted = remove(data.getFilePath().toAbsolutePath().toString());
		if (msg != null) {
			boolean blocked = Common.isEquals(msg.getAction(), "BLOCK");
			if (msg.getMsgFile() != null) {
				bodyDeleted = remove(conf.getPath(msg.getMsgFile(), blocked));
			}
			if (msg.getHeader() != null) {
				headerDeleted = remove(conf.getPath(msg.getHeader(), blocked));
			}

			List<String> appFilePaths = msg.getAppFile();
			for (String path : appFilePaths) {
				attachDeleted += remove(conf.getPath(path, blocked));
			}
			List<String> pcFilePaths = msg.getPcFile();
			for (String path : pcFilePaths) {
				attachDeleted += remove(conf.getPath(path, blocked));
			}

			List<String> embeddedFiles = msg.getEmbeddedFile();
			for (String path : embeddedFiles) {
				embeddedDeleted += remove(path); //embeddedFile의 경우 src 패스가 전체 경로로 된다.
			}
			tempDeleted = removeMessageTempDirectory(msg.getMsgid());
			log.info("DEL_FILE | MSG:{} | BODY:{} | HEADER:{} | ATTACH:{} | EMBEDDED:{} | TEMP:{} | {}", msgDeleted, bodyDeleted, headerDeleted, attachDeleted, embeddedDeleted, tempDeleted, DateUtils.stop(sw));
		}
	}

	private int remove(final String path) {
		if (path == null || path.isEmpty()) return 0;
		try {
			String normalized = Normalizer.normalize(path, Normalizer.Form.NFC);
			log.debug("DEL_FILE | {}", normalized);
			Path filePath = Path.of(normalized);
			if (Files.exists(filePath)) {
				Files.delete(filePath);
				return 1;
			}
		} catch (InvalidPathException e) {
			//ignore
		} catch (Exception e) {
			log.warn("{} | PATH:{} | ERR:{}", ErrorCode.REMOVE_FILE_FAIL, path, e.toString(), e);
		}
		return 0;
	}

	private int removeMessageTempDirectory(final String msgId) {
		if (Common.isEmpty(msgId) || Common.isEmpty(conf.getMemoryDiskPath())) return 0;

		try {
			Path rootPath = Path.of(Normalizer.normalize(conf.getMemoryDiskPath(), Normalizer.Form.NFC)).toAbsolutePath().normalize();
			Path targetPath = rootPath.resolve(Normalizer.normalize(msgId, Normalizer.Form.NFC)).toAbsolutePath().normalize();
			if (!targetPath.startsWith(rootPath) || targetPath.equals(rootPath)) {
				log.warn("DEL_TEMP_SKIP | ROOT:{} | TARGET:{}", rootPath, targetPath);
				return 0;
			}
			if (Files.notExists(targetPath)) {
				return 0;
			}

			int deleted = deleteRecursively(targetPath);
			log.debug("DEL_TEMP | {} | {}", targetPath, deleted);
			return deleted;
		} catch (InvalidPathException e) {
			//ignore
		} catch (Exception e) {
			log.warn("{} | MSGID:{} | ERR:{}", ErrorCode.REMOVE_FILE_FAIL, msgId, e.toString(), e);
		}
		return 0;
	}

	private int deleteRecursively(final Path targetPath) throws IOException {
		try (Stream<Path> paths = Files.walk(targetPath)) {
			List<Path> deleteTargets = paths.sorted(Comparator.reverseOrder()).toList();
			int deleted = 0;
			for (Path path : deleteTargets) {
				Files.deleteIfExists(path);
				deleted++;
			}
			return deleted;
		}
	}
}
