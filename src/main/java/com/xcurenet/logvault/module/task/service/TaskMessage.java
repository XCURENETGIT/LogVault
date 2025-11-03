package com.xcurenet.logvault.module.task.service;

import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.sql.Timestamp;

@Data
@Alias("Message")
public class TaskMessage {
	private String msgId;
	private String taskType;
	private String status;
	private String data;
	private String errorMessage;
	private Timestamp runAt;
	private Timestamp createDt;
}