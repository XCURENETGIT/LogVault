package com.xcurenet.logvault.module.task.process;

import com.alibaba.fastjson2.JSONObject;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.module.task.service.TaskMessage;
import com.xcurenet.logvault.module.task.service.TaskProcessor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;

/**
 * OCR 처리를 담당하는 Processor
 */
@Log4j2
@Component
public class OcrTaskProcessor implements TaskProcessor {

	@Override
	public boolean supports(String taskType) {
		return "OCR".equalsIgnoreCase(taskType);
	}

	@Override
	public void process(TaskMessage message) throws Exception {
		log.info("[OCR] Processing message: {}", message.getMsgId());
		try (FileInputStream in = new FileInputStream("/users/tmp/test_img1.jpg")) {
			// 1️⃣ JSON 파싱
			String json = message.getData();
			JSONObject jsonObj = JSONObject.parseObject(json);

			log.debug("[OCR] Input JSON: {}", json);
			// Jsoup Connection 생성
			Connection connection = Jsoup.connect("http://10.200.10.49:62975/sdk/ocr")
					.timeout(60_000)
					.method(Connection.Method.POST)
					.ignoreContentType(true)
					.data("api_key", "SNOCR-834be64b6228442cac181eb08d84e56c")   // form-data 필드
					.data("type", "upload")
					.data("textout", "true")
					.data("boxes_type", "line")
					.data("image", "sample.png", in);

			// POST 전송 및 응답 수신
			Document response = connection.post();

			// 2️⃣ 실제 OCR 처리 로직 (예시)
			// 실제로는 OCR 엔진 호출, 파일 읽기, 결과 저장 등의 작업 수행
			//Thread.sleep(1000L); // 모의 처리 지연
			log.info("[OCR] OCR 작업 완료: {} {}", message.getMsgId(), response);

			// 3️⃣ 처리 결과 저장 또는 후속 Task enqueue
			// ex) repository.updateStatus(message.getMsgId(), "DONE");
		} catch (Exception e) {
			log.error("[OCR] 처리 중 오류 발생: {}", e.getMessage(), e);
			throw e;
		}
	}
}
