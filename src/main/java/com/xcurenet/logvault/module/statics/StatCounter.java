package com.xcurenet.logvault.module.statics;

import lombok.Data;

import java.util.concurrent.atomic.AtomicLong;

@Data
public class StatCounter {
	private final AtomicLong total = new AtomicLong();
	private final AtomicLong prompt = new AtomicLong();
	private final AtomicLong attach = new AtomicLong();

	public void incPrompt() {
		total.incrementAndGet();
		prompt.incrementAndGet();
	}

	public void incAttach() {
		total.incrementAndGet();
		attach.incrementAndGet();
	}

	public void incTotal() {
		total.incrementAndGet();
	}
}
