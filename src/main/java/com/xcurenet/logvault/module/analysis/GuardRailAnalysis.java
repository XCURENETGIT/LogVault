package com.xcurenet.logvault.module.analysis;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class GuardRailAnalysis {
	private final Config conf;

	public void detect(final ScanData scanData) {
		if (scanData == null || scanData.getEmassDoc() == null) {
			log.warn("MG_GUARD | {}", ErrorCode.GUARDRAIL_MSGDATA_NULL.toString());
			return;
		}

		try {
			detect(scanData.getEmassDoc());
		} catch (Exception e) {
			log.warn("MG_GUARD | {} | {}", ErrorCode.GUARDRAIL_UNKNOWN_ERROR.toString(), e.getMessage(), e);
		}
	}

	public void detect(final EmassDoc doc) {
		if (Common.isNotEquals(doc.getService().getSvc3(), "S")) return; //발신 서비스만

		if (doc.getBody() != null) {
			String body = detectGuardrail(doc.getBody().getText(), "BODY");
			doc.getBody().setGuardrailCategory(body);
		}

		if (doc.getAttach() != null) {
			for (EmassDoc.Attach a : doc.getAttach()) {
				a.setGuardrailCategory(detectGuardrail(a.getText(), "ATTACH | " + a.getSrcPath()));
			}
		}
	}

	public String detectGuardrail(String text, String type) {
		if (Common.isEmpty(text)) return null;
		try {
			JSONObject param = new JSONObject();
			param.put("text", text);
			byte[] body = JSON.toJSONBytes(param, JSONWriter.Feature.LargeObject);

			HttpURLConnection conn = (HttpURLConnection) new URL(conf.getGuardRailApiUrl()).openConnection();
			conn.setRequestMethod("POST");
			conn.setConnectTimeout(60000);
			conn.setReadTimeout(60000);
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			conn.getOutputStream().write(body);
			if (conn.getResponseCode() == 200) {
				String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				log.debug("MG_GUARD | GUARDRAIL_API_RESPONSE | {}", response);

				JSONObject obj = JSONObject.parseObject(response);
				if (!obj.isEmpty()) {
					Map.Entry<String, Object> maxEntry = obj.entrySet().stream().max(Comparator.comparingDouble(e -> Double.parseDouble(e.getValue().toString().replace("%", "")))).orElse(null);
					if (maxEntry.getKey() != null) {
						double val = Double.parseDouble(maxEntry.getValue().toString().replace("%", ""));
						if (conf.getGuardRailLimitRate() > val || conf.getGuardRailLimitLength() > text.length()) {
							log.info("MG_GUARD | {} | {} > {} | {}", type, maxEntry.getKey(), "SAFE", maxEntry.getValue());
							return "SAFE";
						}
						log.info("MG_GUARD | {} | {} | {}", type, maxEntry.getKey(), maxEntry.getValue());
						return maxEntry.getKey();
					}
				} else {
					log.warn("MG_GUARD | {} | TEXT.LENGTH:{} | {}", ErrorCode.GUARDRAIL_ML_API_ERROR.toString(), text.length(), response);
					return null;
				}
			}
		} catch (Exception e) {
			log.error("MG_GUARD | {} | TEXT.LENGTH:{}", ErrorCode.GUARDRAIL_UNKNOWN_ERROR.toString(), text.length(), e);
		}
		return null;
	}
}
