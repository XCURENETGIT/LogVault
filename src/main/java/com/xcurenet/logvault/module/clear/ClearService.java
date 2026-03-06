package com.xcurenet.logvault.module.clear;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.List;

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
		int msgDeleted = remove(data.getFilePath().toAbsolutePath().toString());
		if (msg != null) {
			if (msg.getMsgFile() != null) {
				bodyDeleted = remove(conf.getPath(msg.getMsgFile()));
			}
			if (msg.getHeader() != null) {
				headerDeleted = remove(conf.getPath(msg.getHeader()));
			}

			List<String> appFilePaths = msg.getAppFile();
			for (String path : appFilePaths) {
				attachDeleted += remove(conf.getPath(path));
			}
			List<String> pcFilePaths = msg.getPcFile();
			for (String path : pcFilePaths) {
				attachDeleted += remove(conf.getPath(path));
			}

			List<String> embeddedFiles = msg.getEmbeddedFile();
			for (String path : embeddedFiles) {
				embeddedDeleted += remove(path); //embeddedFile의 경우 src 패스가 전체 경로로 된다.
			}
			log.info("DEL_FILE | MSG:{} | BODY:{} | HEADER:{} | ATTACH:{} | EMBEDDED:{} | {}", msgDeleted, bodyDeleted, headerDeleted, attachDeleted, embeddedDeleted, DateUtils.stop(sw));
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
			log.warn("{} | INVALID_PATH:{} | ERR:{}", ErrorCode.REMOVE_INVALID_PATH, path, e.toString(), e);
		} catch (Exception e) {
			log.warn("{} | PATH:{} | ERR:{}", ErrorCode.REMOVE_FILE_FAIL, path, e.toString(), e);
		}
		return 0;
	}
}
