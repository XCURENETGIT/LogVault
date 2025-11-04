package com.xcurenet.logvault.module.task.service;

import com.alibaba.fastjson2.JSON;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class TaskService {
	private final Config conf;
	private final TaskMessageRepository repository;

	public void send(final ScanData data) {
		StopWatch sw = DateUtils.start();
		EmassDoc doc = data.getEmassDoc();
		try {
			boolean ocrTarget = false;
			if (conf.isOcrApiEnable()) {
				List<EmassDoc.Attach> attaches = doc.getAttach();
				if (attaches == null || attaches.isEmpty()) return;
				for (EmassDoc.Attach attach : attaches) { // 첨부파일 중 하나라도 OCR 대상이라면 처리.
					if (attach.isOcrTarget()) {
						ocrTarget = true;
						break;
					}
				}
			}

			if (ocrTarget) {
				TaskMessage message = new TaskMessage();
				message.setMsgId(doc.getMsgid());
				message.setTaskType("OCR");
				message.setData(JSON.toJSONString(doc));
				repository.insertMessage(message);
			}

			log.info("[OCR_SEND] {} | {}", doc.getMsgid(), DateUtils.stop(sw));
		} catch (Exception e) {
			log.warn("[OCR_SEND] {} | {}", doc.getMsgid(), e.getMessage());
			log.error("", e);
		}
	}
}
