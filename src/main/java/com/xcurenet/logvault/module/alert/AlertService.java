package com.xcurenet.logvault.module.alert;

import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class AlertService {

	public void send(final ScanData data) {
		long startTime = System.currentTimeMillis();
		EmassDoc doc = data.getEmassDoc();
		try {
			log.info("[ALT_SEND] {} | {}", doc.getMsgid(), DateUtils.duration(startTime));
		} catch (Exception e) {
			log.warn("[ALERT] {} | {}", doc.getMsgid(), e.getMessage());
			log.error("", e);
		}
	}
}
