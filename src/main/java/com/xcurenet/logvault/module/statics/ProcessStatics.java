package com.xcurenet.logvault.module.statics;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.LogVaultApplication;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class ProcessStatics {


	@Scheduled(cron = "0 * * * * *")
	private void reportStatisticsByMinute() {
		long processed = LogVaultApplication.getMinuteBy1Count().getAndSet(0);
		log.debug("STATICS_MINUTE | {}", Common.formatNumber(processed));
	}

	@Scheduled(cron = "*/10 * * * * *")
	private void reportStatisticsBySecond() {
		long processed = LogVaultApplication.getSecBy10Count().getAndSet(0);
		log.debug("STATICS_SEC | {}", Common.formatNumber(processed));
	}
}
