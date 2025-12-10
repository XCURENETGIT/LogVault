package com.xcurenet.logvault.module.task.service;

import com.alibaba.fastjson2.JSON;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class TaskService {
	private final Config conf;
	private final TaskMessageRepository repository;

	@Scheduled(cron = "0 0 3 * * *")
	private void cleanup() {
		if (Common.isWindow()) return;

		StopWatch sw = DateUtils.start();
		repository.deleteOldFailed();
		log.info("RM_OCR_FAILED | {}", DateUtils.stop(sw));
	}

	public void send(final ScanData data) {
		EmassDoc doc = data.getEmassDoc();
		if (Common.isNotEquals(doc.getService().getSvc3(), "S")) return; //발신 서비스만

		TaskMessage message = new TaskMessage();
		message.setMsgId(doc.getMsgid());
		if (conf.isOcrApiEnable() && isOcrTarget(data)) {
			message.setTaskType(TaskDispatcherService.TASK_TYPE.OCR.name());
		}

		if (conf.isMlApiEnable() && message.getTaskType() == null) { //ML을 사용하는 경우, OCR을 먼저 처리 후 ML처리
			message.setTaskType(TaskDispatcherService.TASK_TYPE.ML.name());
		}

		if (message.getTaskType() != null) {
			StopWatch sw1 = DateUtils.start();
			message.setData(JSON.toJSONString(doc));
			repository.insertMessage(message);
			log.info("PPS_SEND | {} | {}", message.getTaskType(), DateUtils.stop(sw1));
		}
	}

	private boolean isOcrTarget(final ScanData data) {
		EmassDoc doc = data.getEmassDoc();
		try {
			StopWatch sw = DateUtils.start();
			int ocrTargetCount = 0;
			List<EmassDoc.Attach> attaches = doc.getAttach();
			if (attaches == null || attaches.isEmpty()) return false;
			for (EmassDoc.Attach attach : attaches) { // 첨부파일 중 하나라도 OCR 대상이라면 처리.
				if (attach.isOcrTarget()) {
					ocrTargetCount++;
				}
			}
			if (ocrTargetCount > 0) log.info("OCRREADY | CNT:{} | {}", ocrTargetCount, DateUtils.stop(sw));
			return ocrTargetCount > 0;
		} catch (Exception e) {
			log.warn("OCRREADY | {}", e.getMessage());
			log.error("", e);
		}
		return false;
	}
}
