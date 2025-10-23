package com.xcurenet.logvault.tool.delete;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DataRetentionPolicyManager {

	@Scheduled(cron = "0 0 2 * * *")
	private void cleanupExpiredData() {

	}

	@Scheduled(cron = "0 0 * * * *")
	private void cleanupByStorageLimit() {

	}
}
