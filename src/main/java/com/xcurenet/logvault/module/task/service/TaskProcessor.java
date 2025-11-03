package com.xcurenet.logvault.module.task.service;

public interface TaskProcessor {
	/**
	 * 이 Processor가 처리할 수 있는 taskType인지 판단
	 */
	boolean supports(String taskType);

	/**
	 * 실제 처리 로직
	 */
	void process(TaskMessage message) throws Exception;
}