package com.xcurenet.logvault.module.clear;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class ClearService {
	protected final Config conf;

	public void clear(final ScanData data) {
		StopWatch sw = DateUtils.start();
		MSGData msg = data.getMsgData();

		boolean bodyDeleted = false;
		boolean headerDeleted = false;
		boolean attachDeleted = false;
		remove(data.getFilePath());
		boolean msgDeleted = true;

		if (msg != null) {
			if (msg.getMsgFile() != null) {
				remove(conf.getPath(msg.getMsgFile()));
				bodyDeleted = true;
			}
			if (msg.getHeader() != null) {
				remove(conf.getPath(msg.getHeader()));
				headerDeleted = true;
			}

			List<String> appFilePaths = msg.getAppFile();
			for (String path : appFilePaths) {
				remove(conf.getPath(path));
				attachDeleted = true;
			}
			List<String> pcFilePaths = msg.getPcFile();
			for (String path : pcFilePaths) {
				remove(conf.getPath(path));
				attachDeleted = true;
			}
			log.info("DEL_FILE | MSG:{} | BODY:{} | HEADER:{} | ATTACH:{} | {}", msgDeleted, bodyDeleted, headerDeleted, attachDeleted, DateUtils.stop(sw));
		}
	}

	private void remove(final String path) {
		if (path == null || path.isEmpty()) return;
		try {
			log.debug("DEL_FILE | {}", path);
			Files.deleteIfExists(Paths.get(path));
		} catch (Exception e) {
			log.warn("{}", path, e);
		}
	}
}
