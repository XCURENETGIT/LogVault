package com.xcurenet.logvault.module.alert;

import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

@Log4j2
@Service
@RequiredArgsConstructor
public class AlertService {

	public void send(final ScanData data) {
		StopWatch sw = DateUtils.start();
		EmassDoc doc = data.getEmassDoc();
		try {
			log.info("ALT_SEND | {}", DateUtils.stop(sw));
		} catch (Exception e) {
			log.warn("ALT_SEND | {}", e.getMessage());
			log.error("", e);
		}
	}
}
