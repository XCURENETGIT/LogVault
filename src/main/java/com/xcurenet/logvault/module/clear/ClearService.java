package com.xcurenet.logvault.module.clear;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class ClearService {
	protected final Config conf;

	public void clear(final ScanData data) {
		long startTime = System.currentTimeMillis();
		MSGData msg = data.getMsgData();
		remove(data.getFilePath(), msg);

		if (msg == null) return;
		if (msg.getMsgFilePath() != null) remove(msg.getMsgFilePath(), msg);
		if (msg.getHeaderPath() != null) remove(msg.getHeaderPath(), msg);

		List<String> appFilePaths = msg.getAppFilePath();
		for (String path : appFilePaths) {
			remove(path, msg);
		}
		log.info("[DEL_FILE] {} | {}", msg.getMsgid(), DateUtils.duration(startTime));
	}

	private void remove(final String path, final MSGData msg) {
		if (path == null || path.isEmpty() || !new File(path).exists()) return;
		try {
			log.debug("[DEL_FILE] {} | {}", msg.getMsgid(), path);
			if (CommonUtil.isWindow()) return;
			FileUtils.delete(new File(path));
		} catch (Exception e) {
			log.warn("{}", path, e);
		}
	}
}
