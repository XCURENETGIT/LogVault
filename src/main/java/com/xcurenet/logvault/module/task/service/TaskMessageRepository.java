package com.xcurenet.logvault.module.task.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Mapper
public interface TaskMessageRepository {

	@Transactional
	List<TaskMessage> claimBatchByType(@Param("taskType") String taskType, @Param("limit") int limit);

	@Transactional
	void markInProgressBatch(@Param("msgIds") List<String> ids);

	@Transactional
	void deleteById(@Param("msgId") String id);

	@Transactional
	void updateStatusFailed(@Param("msgId") String id, @Param("err") String err);

	@Transactional
	void updateStatusDone(@Param("msgId") String id);

	@Transactional
	void insertMessage(TaskMessage message);
}