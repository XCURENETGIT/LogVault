package com.xcurenet.logvault.module.task.service;

import com.alibaba.fastjson2.JSON;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

@Log4j2
@Service
@RequiredArgsConstructor
public class TaskService {
	private final TaskMessageRepository repository;

	public void send(final ScanData data) {
		StopWatch sw = DateUtils.start();
		EmassDoc doc = data.getEmassDoc();
		try {
			TaskMessage message = new TaskMessage();
			message.setMsgId(doc.getMsgid());
			message.setTaskType("OCR");
			message.setData(JSON.toJSONString(doc));
			repository.insertMessage(message);

			log.info("[OCR_SEND] {} | {}", doc.getMsgid(), DateUtils.stop(sw));
		} catch (Exception e) {
			log.warn("[OCR_SEND] {} | {}", doc.getMsgid(), e.getMessage());
			log.error("", e);
		}
	}
}
