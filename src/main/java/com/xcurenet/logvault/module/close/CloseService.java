package com.xcurenet.logvault.module.close;

import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class CloseService {
	protected final Config conf;

	public void close(final ScanData data) {

	}
}
