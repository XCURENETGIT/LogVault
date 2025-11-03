package com.xcurenet.logvault.module.task.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TaskProcessorRegistry {
	private final List<TaskProcessor> processors;

	public TaskProcessor find(String taskType) {
		return processors.stream()
				.filter(p -> p.supports(taskType))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unsupported task type: " + taskType));
	}
}
