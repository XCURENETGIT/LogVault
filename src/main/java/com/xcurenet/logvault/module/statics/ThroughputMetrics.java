package com.xcurenet.logvault.module.statics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ThroughputMetrics {

	private final Counter counter;

	public ThroughputMetrics(MeterRegistry registry) {
		this.counter = Counter.builder("app.processed.count").description("Number of processed items").tag("type", "default").register(registry);
	}

	public void increment() {
		counter.increment();
	}
}
