package com.xcurenet.logvault.module.task.process;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.module.task.service.TaskMessage;
import com.xcurenet.logvault.module.task.service.TaskProcessor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * ML_ANALYSIS 처리를 담당하는 Processor
 */
@Log4j2
@Component
public class MLTaskProcessor implements TaskProcessor {

	@Override
	public boolean supports(String taskType) {
		return "ML_ANALYSIS".equalsIgnoreCase(taskType);
	}

	@Override
	public void process(TaskMessage message) throws Exception {
		log.info("ML_ANALYSIS | Processing message: {}", message.getMsgId());
		try {
			// 1️⃣ JSON 파싱
			String json = message.getData();
			log.debug("ML_ANALYSIS | Input JSON: {}", json);
			Common.sleep(1000L);

			// 2️⃣ 실제 ML_ANALYSIS 처리 로직 (예시)
			// 실제로는 ML_ANALYSIS 엔진 호출, 파일 읽기, 결과 저장 등의 작업 수행
			//Thread.sleep(1000L); // 모의 처리 지연
			log.info("ML_ANALYSIS | ML_ANALYSIS 작업 완료: {}", message.getMsgId());

			// 3️⃣ 처리 결과 저장 또는 후속 Task enqueue
			// ex) repository.updateStatus(message.getMsgId(), "DONE");
		} catch (Exception e) {
			log.error("ML_ANALYSIS | 처리 중 오류 발생: {}", e.getMessage(), e);
			throw e;
		}
	}
}
