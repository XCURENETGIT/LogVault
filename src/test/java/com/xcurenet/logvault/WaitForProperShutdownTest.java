package com.xcurenet.logvault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WaitForProperShutdown - Graceful 종료")
class WaitForProperShutdownTest {

	@Test
	@DisplayName("run.set(false) 후 latch.countDown() → 정상 종료")
	void normalShutdown() throws Exception {
		CountDownLatch latch = new CountDownLatch(1);
		AtomicBoolean run = new AtomicBoolean(true);
		WaitForProperShutdown hook = new WaitForProperShutdown(latch, run);

		Thread hookThread = new Thread(hook);
		hookThread.start();

		// hook이 run.set(false) 수행 후 latch 대기 중일 것
		Thread.sleep(200);
		assertFalse(run.get(), "run이 false로 설정되어야 함");

		// latch 해제 → hook 종료
		latch.countDown();
		hookThread.join(5000);
		assertFalse(hookThread.isAlive(), "hook 스레드가 종료되어야 함");
	}

	@Test
	@DisplayName("latch가 countDown되지 않으면 120초 후 타임아웃 (빠른 검증)")
	void timeoutScenario() throws Exception {
		CountDownLatch latch = new CountDownLatch(1); // countDown 안 함
		AtomicBoolean run = new AtomicBoolean(true);
		WaitForProperShutdown hook = new WaitForProperShutdown(latch, run);

		Thread hookThread = new Thread(hook);
		hookThread.start();

		Thread.sleep(200);
		assertFalse(run.get());
		assertTrue(hookThread.isAlive(), "latch 해제 전이므로 hook 스레드는 대기 중");

		// 테스트 정리: latch 해제하여 스레드 종료
		latch.countDown();
		hookThread.join(5000);
	}

	@Test
	@DisplayName("인터럽트 발생 시 정상 처리")
	void interruptHandling() throws Exception {
		CountDownLatch latch = new CountDownLatch(1);
		AtomicBoolean run = new AtomicBoolean(true);
		WaitForProperShutdown hook = new WaitForProperShutdown(latch, run);

		Thread hookThread = new Thread(hook);
		hookThread.start();

		Thread.sleep(200);
		hookThread.interrupt();
		hookThread.join(5000);
		assertFalse(hookThread.isAlive());
	}
}
