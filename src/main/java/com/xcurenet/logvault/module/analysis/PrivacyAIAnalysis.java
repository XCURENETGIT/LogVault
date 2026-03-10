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
import java.util.List;
import java.util.concurrent.TimeUnit;

@Log4j2
@Service
@RequiredArgsConstructor
public class PrivacyAIAnalysis {

	private final Config conf;

	private PiiDetectorGrpc.PiiDetectorBlockingStub stub;
	private ManagedChannel channel;

	/**
	 * ML API가 반환하는 키(탐지 타입)
	 * processText()에서 이 순서대로 JSON 배열을 읽는다.
	 */
	private static final String[] PI_TYPE = {"SN", "DN", "AN", "PN", "MN", "BN", "EML", "IP", "SSN"};

	/**
	 * 긴 텍스트 split 처리용 상수
	 * 현재는 기존 코드와 동일하게 유지.
	 * 필요 시 추후 processText() 내부에서 분할 호출로 확장 가능.
	 */
	private static final int CHUNK_SIZE = 10_000;
	private static final int OVERLAP = 200;
	private static final int STEP = CHUNK_SIZE - OVERLAP;

	@PostConstruct
	public void initGrpcClient() {
		this.channel = ManagedChannelBuilder.forAddress(conf.getMlPrivacyGrpcHost(), conf.getMlPrivacyGrpcPort()).usePlaintext().build();
		this.stub = PiiDetectorGrpc.newBlockingStub(channel);
		log.info("PII_INIT | PII gRPC Client Initialized | {}:{}", conf.getMlPrivacyGrpcHost(), conf.getMlPrivacyGrpcPort());
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

		JSONObject api = detectPII(text);
		if (api == null) {
			log.info("REG_DONE | {} | PII_API_NULL | TEXT.LENGTH:{} | {}", type, text.length(), DateUtils.stop(sw));
			return 0;
		}

		for (String key : PI_TYPE) {
			JSONArray datas = api.getJSONArray(key);
			if (datas == null || datas.isEmpty()) continue;

			EmassDoc.PrivacyInfo info = toPrivacyInfo(key, type, attachName, datas);
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

	private EmassDoc.PrivacyInfo toPrivacyInfo(String key, String type, String attachName, JSONArray datas) {
		if (!PatternLoader.isDetectCode(key)) {
			log.debug("REG_INFO | {} | KEY:{}", ErrorCode.PRIVACY_DETECT_CODE_INVALID.toString(), key);
			return null;
		}

		List<String> items = new ArrayList<>();
		for (int i = 0; i < datas.size(); i++) {
			JSONObject it = datas.getJSONObject(i);
			if (it == null) continue;

			String matchString = it.getString("matchString");
			if (Common.isEmpty(matchString)) continue;

			String encrypted = Common.encString(matchString.getBytes(StandardCharsets.UTF_8), conf.getEncryptKey(), conf.getEncyptCipher());
			if (encrypted != null) {
				items.add(encrypted);
			}
		}
		if (items.isEmpty()) return null;

		int threshold = PatternLoader.getCodeValueOrDefault(key, 1);
		if (items.size() < threshold) {
			log.debug("REG_INFO | {} | KEY:{} COUNT:{} THRESHOLD:{}", ErrorCode.PRIVACY_THRESHOLD_NOT_MET.toString(), key, items.size(), threshold);
			return null;
		}

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

		try {
			Pii.DetectRequest req = Pii.DetectRequest.newBuilder().setText(text).setMaxResultsPerType(max).setRuleset("strict").build();
			Pii.DetectResponse res = stub.detect(req);
			if (!res.getSuccess()) {
				log.warn("{} | TEXT.LENGTH:{} | STATUS:{} | MESSAGE:{}", ErrorCode.PRIVACY_ML_API_ERROR.toString(), text.length(), res.getStatus(), res.getMessage());
				return null;
			}

			JSONObject result = new JSONObject();
			Pii.PiiData data = res.getData();
			addMatches(result, "SN", data.getSnList());
			addMatches(result, "DN", data.getDnList());
			addMatches(result, "AN", data.getAnList());
			addMatches(result, "PN", data.getPnList());
			addMatches(result, "MN", data.getMnList());
			addMatches(result, "BN", data.getBnList());
			addMatches(result, "EML", data.getEmlList());
			addMatches(result, "IP", data.getIpList());
			addMatches(result, "SSN", data.getSsnList());
			log.debug("ML_PRIVACY_GRPC_RESPONSE | TEXT.LENGTH:{} | META:ruleset={}, version={}, updatedAt={}", text.length(), res.getMeta().getRulesetName(), res.getMeta().getRulesetVersion(), res.getMeta().getRulesetUpdatedAt());
			return result;
		} catch (Exception e) {
			log.error("{} | TEXT.LENGTH:{}", ErrorCode.PRIVACY_ML_API_ERROR.toString(), text.length(), e);
			return null;
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