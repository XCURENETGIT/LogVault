package com.xcurenet.logvault.module.analysis;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.loader.PatternLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import xcn.pii.v1.Pii;
import xcn.pii.v1.PiiDetectorGrpc;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

@Log4j2
@Service
@RequiredArgsConstructor
public class PrivacyAIAnalysis {

	private final Config conf;
	private PiiDetectorGrpc.PiiDetectorBlockingStub stub;
	private ManagedChannel channel;
	private final AtomicInteger grpcConsecutiveFailures = new AtomicInteger(0);
	private volatile long circuitOpenUntilMs = 0L;

	/**
	 * ML API가 반환하는 키(탐지 타입)
	 * processText()에서 이 순서대로 JSON 배열을 읽는다.
	 */
	private static final String[] PI_TYPE = {"SN", "DN", "AN", "PN", "MN", "BN", "EML", "IP", "SSN", "BRN", "FN", "VN_CCCD", "VN_MN", "VN_PN", "VN_TIN", "VN_SI"};

	@PostConstruct
	public void initGrpcClient() {
		ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forAddress(conf.getMlPrivacyGrpcHost(), conf.getMlPrivacyGrpcPort());
		if (!conf.isMlPrivacyGrpcTlsEnable()) {
			channelBuilder.usePlaintext();
		}
		this.channel = channelBuilder.build();
		this.stub = PiiDetectorGrpc.newBlockingStub(channel);
		log.info("PII_INIT | PII gRPC Client Initialized | {}:{} | TLS:{} | DEADLINE:{}ms | RETRY:{}", conf.getMlPrivacyGrpcHost(), conf.getMlPrivacyGrpcPort(), conf.isMlPrivacyGrpcTlsEnable(), conf.getMlPrivacyGrpcDeadlineMs(), conf.getMlPrivacyGrpcRetryMaxAttempts());
	}

	@PreDestroy
	public void destroy() {
		if (channel != null) {
			try {
				channel.shutdown();
				if (!channel.awaitTermination(3, TimeUnit.SECONDS)) {
					channel.shutdownNow();
				}
			} catch (Exception e) {
				log.warn("PII_DESTROY | gRPC channel shutdown error | {}", e.getMessage(), e);
				channel.shutdownNow();
			}
		}
	}

	public void detect(final ScanData scanData) {
		if (scanData == null || scanData.getEmassDoc() == null) {
			log.warn("{}", ErrorCode.PRIVACY_MSGDATA_NULL.toString());
			return;
		}

		try {
			detect(scanData.getEmassDoc());
		} catch (Exception e) {
			log.warn("{} | {}", ErrorCode.PRIVACY_UNKNOWN_ERROR.toString(), e.getMessage(), e);
		}
	}

	public void detect(final EmassDoc doc) {
		if (doc == null) {
			return;
		}

		doc.setPrivacyInfo(null);
		doc.setPrivacyTotal(0);

		int total = 0;
		if (doc.getBody() != null) {
			total += processText(doc, doc.getBody().getText(), "B", "-");
		}

		if (doc.getAttach() != null) {
			for (EmassDoc.Attach attach : doc.getAttach()) {
				if (attach == null) continue;
				total += processText(doc, attach.getText(), "A", attach.getName());
			}
		}

		if (total == 0) {
			doc.setPrivacyInfo(null);
		}
		doc.setPrivacyTotal(total);
	}

	private static List<EmassDoc.PrivacyInfo> ensurePrivacyInfoList(EmassDoc doc) {
		if (doc.getPrivacyInfo() == null) {
			doc.setPrivacyInfo(new ArrayList<>());
		}
		return doc.getPrivacyInfo();
	}

	private int processText(EmassDoc doc, String text, String type, String attachName) {
		if (Common.isEmpty(text)) {
			return 0;
		}

		StopWatch sw = DateUtils.start();
		StringBuilder sb = new StringBuilder();
		List<EmassDoc.PrivacyInfo> bucket = ensurePrivacyInfoList(doc);
		int added = 0;

		Map<String, List<Pii.MatchItem>> matchesByType = detectPIIMatches(text, 5000);
		if (matchesByType == null) {
			log.info("REG_DONE | {} | PII_API_NULL | TEXT.LENGTH:{} | {}", type, text.length(), DateUtils.stop(sw));
			return 0;
		}

		for (String key : PI_TYPE) {
			List<Pii.MatchItem> matches = matchesByType.get(key);
			if (matches == null || matches.isEmpty()) continue;

			EmassDoc.PrivacyInfo info = toPrivacyInfo(key, type, attachName, matches);
			log.debug("type:{}, info:{}", type, info);
			if (info == null) continue;

			bucket.add(info);
			added += info.getCount();
			sb.append(key).append(":").append(info.getCount()).append(" ");
		}

		if (Common.isNotEmpty(sb.toString())) {
			log.info("REG_DONE | {} | {} | TEXT.LENGTH:{} | {}", type, sb.toString().trim(), text.length(), DateUtils.stop(sw));
		} else {
			log.info("REG_DONE | {} | PII_NONE | TEXT.LENGTH:{} | {}", type, text.length(), DateUtils.stop(sw));
		}

		doc.setPrivacyInfo(bucket);
		return added;
	}

	private EmassDoc.PrivacyInfo toPrivacyInfo(String key, String type, String attachName, List<Pii.MatchItem> matches) {
		if (!PatternLoader.isDetectCode(key)) {
			log.info("REG_INFO | {} | KEY:{}", ErrorCode.PRIVACY_DETECT_CODE_INVALID.toString(), key);
			return null;
		}

		List<String> items = new ArrayList<>();
		for (Pii.MatchItem match : matches) {
			if (match == null) continue;
			String matchString = match.getMatchString();
			if (Common.isEmpty(matchString)) continue;

			String encrypted = Common.encString(matchString.getBytes(StandardCharsets.UTF_8), conf.getEncryptKey(), conf.getEncyptCipher());
			if (encrypted != null) {
				items.add(encrypted);
			} else {
				log.warn("[REG_WARN] {} | {} | {}", ErrorCode.PRIVACY_DETECT_CODE_INVALID.toString(), key, matchString);
			}
		}
		if (items.isEmpty()) return null;

		EmassDoc.PrivacyInfo info = new EmassDoc.PrivacyInfo();
		info.setId(key);
		info.setType(type);
		info.setAttachName(attachName);
		info.setPrivacyData(new ArrayList<>(items));
		info.setCount(items.size());
		return info;
	}

	public JSONObject detectPII(String text) {
		return detectPII(text, 5000);
	}

	/**
	 * gRPC 응답을 기존 REST API의 data JSON 구조와 동일하게 변환한다.
	 */
	public JSONObject detectPII(String text, int max) {
		if (Common.isEmpty(text)) {
			return null;
		}

		Map<String, List<Pii.MatchItem>> matchesByType = detectPIIMatches(text, max);
		if (matchesByType == null) {
			return null;
		}

		JSONObject result = new JSONObject();
		for (String key : PI_TYPE) {
			addMatches(result, key, matchesByType.get(key));
		}
		return result;
	}

	private Map<String, List<Pii.MatchItem>> detectPIIMatches(String text, int max) {
		if (isCircuitOpen()) {
			log.warn("{} | TEXT.LENGTH:{} | CIRCUIT:OPEN(until={})", ErrorCode.PRIVACY_ML_API_ERROR.toString(), text.length(), circuitOpenUntilMs);
			return null;
		}

		int maxAttempts = Math.max(1, conf.getMlPrivacyGrpcRetryMaxAttempts());
		int deadlineMs = Math.max(500, conf.getMlPrivacyGrpcDeadlineMs());
		int backoffMs = Math.max(0, conf.getMlPrivacyGrpcRetryBackoffMs());
		Pii.DetectRequest req = Pii.DetectRequest.newBuilder().setText(text).setMaxResultsPerType(max).setRuleset("default").build();

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				Pii.DetectResponse res = stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS).detect(req);
				if (!res.getSuccess()) {
					log.warn("{} | TEXT.LENGTH:{} | ATTEMPT:{}/{} | STATUS:{} | MESSAGE:{}", ErrorCode.PRIVACY_ML_API_ERROR.toString(), text.length(), attempt, maxAttempts, res.getStatus(), res.getMessage());
					if (attempt < maxAttempts) {
						sleepRetry(backoffMs, attempt);
						continue;
					}
					markGrpcFailure();
					return null;
				}

				markGrpcSuccess();
				Pii.PiiData data = res.getData();
				Map<String, List<Pii.MatchItem>> result = new LinkedHashMap<>();
				result.put("SN", data.getSnList());
				result.put("SSN", data.getSsnList());
				result.put("DN", data.getDnList());
				result.put("PN", data.getPnList());
				result.put("MN", data.getMnList());
				result.put("BN", data.getBnList());
				result.put("EML", data.getEmlList());
				result.put("CN", data.getCnList());
				result.put("AN", data.getAnList());
				result.put("BRN", data.getBrnList());
                result.put("FN", data.getFnList());
                result.put("VN_CCCD", data.getVnCccdList());
                result.put("VN_MN", data.getVnMnList());
                result.put("VN_PN", data.getVnPnList());
                result.put("VN_TIN", data.getVnTinList());
                result.put("VN_SI", data.getVnSiList());
				log.info("{}", result);
				log.debug("ML_PRIVACY_GRPC_RESPONSE | TEXT.LENGTH:{} | META:ruleset={}, version={}, updatedAt={}", text.length(), res.getMeta().getRulesetName(), res.getMeta().getRulesetVersion(), res.getMeta().getRulesetUpdatedAt());
				return result;
			} catch (StatusRuntimeException e) {
				log.warn("{} | TEXT.LENGTH:{} | ATTEMPT:{}/{} | STATUS:{}", ErrorCode.PRIVACY_ML_API_ERROR.toString(), text.length(), attempt, maxAttempts, e.getStatus(), e);
				if (attempt < maxAttempts) {
					sleepRetry(backoffMs, attempt);
					continue;
				}
				markGrpcFailure();
				log.error("{} | TEXT.LENGTH:{}", ErrorCode.PRIVACY_ML_API_ERROR.toString(), text.length(), e);
				return null;
			} catch (Exception e) {
				log.warn("{} | TEXT.LENGTH:{} | ATTEMPT:{}/{} | {}", ErrorCode.PRIVACY_ML_API_ERROR.toString(), text.length(), attempt, maxAttempts, e.getMessage(), e);
				if (attempt < maxAttempts) {
					sleepRetry(backoffMs, attempt);
					continue;
				}
				markGrpcFailure();
				log.error("{} | TEXT.LENGTH:{}", ErrorCode.PRIVACY_ML_API_ERROR.toString(), text.length(), e);
				return null;
			}
		}
		return null;
	}

	private boolean isCircuitOpen() {
		return System.currentTimeMillis() < circuitOpenUntilMs;
	}

	private void markGrpcSuccess() {
		grpcConsecutiveFailures.set(0);
		circuitOpenUntilMs = 0L;
	}

	private void markGrpcFailure() {
		int failures = grpcConsecutiveFailures.incrementAndGet();
		int threshold = Math.max(1, conf.getMlPrivacyGrpcCircuitFailureThreshold());
		if (failures >= threshold) {
			circuitOpenUntilMs = System.currentTimeMillis() + Math.max(1000, conf.getMlPrivacyGrpcCircuitOpenMs());
			grpcConsecutiveFailures.set(0);
			log.warn("{} | CIRCUIT:OPEN | THRESHOLD_REACHED:{} | OPEN_MS:{}", ErrorCode.PRIVACY_ML_API_ERROR.toString(), threshold, conf.getMlPrivacyGrpcCircuitOpenMs());
		}
	}

	private void sleepRetry(int backoffMs, int attempt) {
		if (backoffMs <= 0) {
			return;
		}
		try {
			Thread.sleep((long) backoffMs * attempt);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private void addMatches(JSONObject result, String key, List<Pii.MatchItem> matches) {
		if (matches == null || matches.isEmpty()) {
			return;
		}
		JSONArray arr = new JSONArray();
		for (Pii.MatchItem match : matches) {
			if (match == null) {
				continue;
			}

			String matchString = match.getMatchString();
			if (Common.isEmpty(matchString)) {
				continue;
			}

			JSONObject obj = new JSONObject();
			obj.put("start", match.getStart());
			obj.put("end", match.getEnd());
			obj.put("matchString", matchString);
			arr.add(obj);
		}
		if (!arr.isEmpty()) {
			result.put(key, arr);
		}
	}

	/**
	 * 기존 REST API 직접 호출 버전
	 * 비교/장애 대응/롤백용으로 남겨둠
	 */
	public JSONObject detectPII_RestAPI(String text, int max) {
		if (Common.isEmpty(text)) {
			return null;
		}

		try {
			JSONObject param = new JSONObject();
			param.put("text", text);
			param.put("max_results_per_type", max);

			byte[] body = JSON.toJSONBytes(param, JSONWriter.Feature.LargeObject);
			HttpURLConnection conn = (HttpURLConnection) new URL(conf.getMlPrivacyApiUrl()).openConnection();
			conn.setRequestMethod("POST");
			conn.setConnectTimeout(60000);
			conn.setReadTimeout(60000);
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			try (OutputStream os = conn.getOutputStream()) {
				os.write(body);
				os.flush();
			}

			int responseCode = conn.getResponseCode();
			if (responseCode == 200) {
				String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				log.debug("ML_PRIVACY_API_RESPONSE | {}", response);
				JSONObject obj = JSONObject.parseObject(response);
				if (Boolean.TRUE.equals(obj.getBoolean("success"))) {
					return obj.getJSONObject("data");
				}
				log.warn("{} | TEXT.LENGTH:{} | {}", ErrorCode.PRIVACY_ML_API_ERROR.toString(), text.length(), response);
			} else {
				log.warn("REG_INFO | RESPONSE ERROR | {} | TEXT.LENGTH:{} | HTTP:{}", ErrorCode.PRIVACY_ML_API_ERROR.toString(), text.length(), responseCode);
			}
		} catch (Exception e) {
			log.error("{} | TEXT.LENGTH:{}", ErrorCode.PRIVACY_ML_API_ERROR.toString(), text.length(), e);
		}

		return null;
	}
}
