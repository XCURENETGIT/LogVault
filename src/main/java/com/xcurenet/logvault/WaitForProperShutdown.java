package com.xcurenet.logvault;

import lombok.extern.log4j.Log4j2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class WaitForProperShutdown extends Thread {
	private final CountDownLatch shutdownLatch;
	private final AtomicBoolean run;

	public WaitForProperShutdown(final CountDownLatch shutdownLatch, final AtomicBoolean run) {
		this.shutdownLatch = shutdownLatch;
		this.run = run;
	}

	@Override
	public void run() {
		log.info("APP_STOP | Waiting for all active workers to complete ongoing tasks safely...");
		run.set(false);
		try {
			if (!shutdownLatch.await(120, TimeUnit.SECONDS)) {
				log.warn("APP_STOP | Shutdown latch timeout (120s) reached. Forcing shutdown.");
			} else {
				log.info("APP_STOP | Shutdown latch released properly.");
			}
		} catch (final InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}
}
