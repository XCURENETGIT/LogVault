package com.xcurenet.common.utils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {
	private final String namePrefix;
	private final AtomicInteger threadNumber = new AtomicInteger(1);

	public NamedThreadFactory(String namePrefix) {
		this.namePrefix = namePrefix;
	}

	@Override
	public Thread newThread(Runnable r) {
		Thread t = new Thread(r);
		t.setName(namePrefix + "-" + threadNumber.getAndIncrement());
		return t;
	}
}