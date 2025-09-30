package com.xcurenet.logvault.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxmind.geoip2.record.Location;
import com.xcurenet.common.geo.GeoLocation;
import com.xcurenet.common.types.IP;
import com.xcurenet.common.useragent.AgentService;
import com.xcurenet.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.geo.GeoPoint;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.data.client.orhlc.OpenSearchRestTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;
import ua_parser.Client;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Log4j2
@Service
@RequiredArgsConstructor
public class LogTempService {
	private final OpenSearchRestTemplate template;
	private final RestHighLevelClient client;
	private final GeoLocation geoLocation;
	private final AgentService agentService;
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final String[] SVCS = {"IGPS", "IXZS", "IYAS", "IAWS", "ITWR", "IXFR", "ISHR", "IXBS", "IAGR", "ISHS"};

	private EmassDoc.Service getService() {
		String svc = SVCS[ThreadLocalRandom.current().nextInt(SVCS.length)];
		char[] chars = svc.toCharArray();

		EmassDoc.Service service = new EmassDoc.Service();
		service.setSvc(svc);
		service.setSvc1(String.valueOf(chars[0]));
		service.setSvc2(String.valueOf(chars[1]));
		service.setSvc3(String.valueOf(chars[2]));
		service.setSvc4(String.valueOf(chars[3]));
		service.setSvc12("" + chars[0] + chars[1]);
		return service;
	}

	private EmassDoc.Day getDay(EmassDoc doc) {
		var date = doc.getTimestamp().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		int week = date.get(ChronoField.ALIGNED_WEEK_OF_YEAR);
		int dayOfWeek = date.getDayOfWeek().getValue();

		EmassDoc.Day day = new EmassDoc.Day();
		day.setWeek(week);
		day.setWork((dayOfWeek == 6 || dayOfWeek == 7) ? "H" : "W"); // 토/일 = H, 나머지 = W
		return day;
	}

	private String randomIp() {
		return String.format("%d.%d.%d.%d",
				ThreadLocalRandom.current().nextInt(1, 256),
				ThreadLocalRandom.current().nextInt(0, 256),
				ThreadLocalRandom.current().nextInt(0, 256),
				ThreadLocalRandom.current().nextInt(1, 255));
	}

	private EmassDoc.Network getNetwork() throws IOException {
		String srcIp = randomIp();
		String dstIp = randomIp();
		Location srcLocation = geoLocation.getLocation(new IP(srcIp), GeoLocation.KR_LATITUDE, GeoLocation.KR_LONGITUDE);
		Location dstLocation = geoLocation.getLocation(new IP(dstIp), GeoLocation.EN_LATITUDE, GeoLocation.EN_LONGITUDE);
		String srcCountry = geoLocation.getCountryCode(new IP(srcIp));
		String dstCountry = geoLocation.getCountryCode(new IP(dstIp));

		EmassDoc.Network network = new EmassDoc.Network();
		network.setProtocol("http");
		network.setSrcPort(ThreadLocalRandom.current().nextInt(1024, 65535)); // 동적 포트
		network.setSrcIp(srcIp);
		network.setSrcCountry(srcCountry);
		network.setSrcLocation(new GeoPoint(srcLocation.getLatitude(), srcLocation.getLongitude()));
		network.setDstPort(443);
		network.setDstIp(dstIp);
		network.setDstCountry(dstCountry);
		network.setDstLocation(new GeoPoint(dstLocation.getLatitude(), dstLocation.getLongitude()));
		return network;
	}

	private EmassDoc.Http getHttp(long id) {
		final String[] UA_STRINGS = {
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Whale/4.33.325.17 Safari/537.36",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36 Edg/139.0.0.0",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Code/1.104.1 Chrome/138.0.7204.235 Electron/37.3.1 Safari/537.36",
				"Mozilla/5.0 (Windows NT 10.0.19045; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Slack/4.46.96 Chrome/140.0.7339.41 Electron/38.0.0 Safari/537.36",
				"Mozilla/5.0 (Windows NT 10.0.19045; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Slack/4.46.96 Chrome/140.0.7339.41 Electron/38.0.0 Safari/537.36 OS_Product/Workstation Servicing_Channel/SAC DDL Sonic Slack_SSB/4.46.96",
				"Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 10.0; Win64; .NET4.0C; .NET4.0E; .NET CLR 2.0.50727; .NET CLR 3.0.30729; .NET CLR 3.5.30729; Tablet PC 2.0; IDCRL"
		};
		String raw = UA_STRINGS[(int) (id % UA_STRINGS.length)];

		Client client = agentService.parse(raw);

		EmassDoc.Agent agent = new EmassDoc.Agent();
		agent.setRaw(raw);
		agent.setDevice(client.device.family);
		agent.setOs(client.os.family);
		agent.setOsVersion(client.os.major);
		agent.setClient(client.userAgent.family);
		agent.setClientVersion(
				String.join(".",
						client.userAgent.major != null ? client.userAgent.major : "0",
						client.userAgent.minor != null ? client.userAgent.minor : "0",
						client.userAgent.patch != null ? client.userAgent.patch : "0"
				)
		);

		EmassDoc.Http http = new EmassDoc.Http();
		http.setUrl("https://www.chatgpt.com/prompt?name=aaaa");
		http.setAgent(agent);
		return http;
	}

	private EmassDoc.User getUser() {
		EmassDoc.User user = new EmassDoc.User();
		user.setIp("1.225.49.111");
		user.setId("jochangmin");
		user.setName("조창민");
		user.setCeo(false);
		user.setDeptCode("D00-01");
		user.setDeptName("선행개발팀");
		user.setJikgubCode("J00-01");
		user.setJikgubName("부장");
		return user;
	}

	private EmassDoc.Body getBody() {
		EmassDoc.Body body = new EmassDoc.Body();
		body.setSize(2323);
		body.setText("SAMSUNG Wallet 안녕하세요, 삼성월렛 입니다. 공유  410519   2210921 정보통신망 이용촉진 및 정보보호 등에 관한 법률 제30조의 2 및 동법 시행령 제17조에 의거하여 2025년 6월 11일까지 가입하신 회원님들의 개인정보 이용내역을 다음과 같이 알려드립니다. ▣ 수집하는 개인정보 항목 삼성전자는 사용자가 회원을 가입하거나 서비스를 이용할 때, 다음과 같은 개인정보를");
		return body;
	}

	private List<EmassDoc.Attach> getAttach() {
		List<EmassDoc.Attach> result = new ArrayList<>();

		EmassDoc.Attach attach = new EmassDoc.Attach();
		attach.setId("1234567890");
		attach.setName("test.txt");
		attach.setHasName(true);
		attach.setHash("1234567890");
		attach.setSize(23232L);
		attach.setText("SAMSUNG Wallet 안녕하세요, 삼성월렛 입니다. 공유  정보통신망 이용촉진 및 정보보호 등에 관한 법률 제30조의 2 및 동법 시행령 제17조에 의거하여 2025년 6월 11일까지 가입하신 회원님들의 개인정보 이용내역을 다음과 같이 알려드립니다. ▣ 수집하는 개인정보 항목 삼성전자는 사용자가 회원을 가입하거나 서비스를 이용할 때, 다음과 같은 개인정보를");
		result.add(attach);
		return result;
	}

	private List<EmassDoc.PrivacyInfo> getPrivacyInfo() {
		List<EmassDoc.PrivacyInfo> result = new ArrayList<>();

		EmassDoc.PrivacyInfo privacy = new EmassDoc.PrivacyInfo();
		privacy.setId("MCN");
		privacy.setType("B");
		privacy.setAttachName("-");
		privacy.setKeywords(List.of("410519   2210921"));
		privacy.setCount(1);
		result.add(privacy);
		return result;
	}

	private static final List<String> KEYWORD_POOL = List.of(
			"test", "정보", "공유", "보안", "주간보고", "월간보고", "결재", "기안", "회의록", "프로젝트",
			"검토", "승인", "요청", "보고", "계획", "성과", "이슈", "조치", "담당자", "팀장",
			"기록", "정책", "문서", "결과", "확인", "이력", "관리", "내역", "공지", "공지사항",
			"java", "script", "python", "go", "rust", "csharp", "typescript", "react", "spring", "docker",
			"kubernetes", "maven", "gradle", "jenkins", "gitlab", "vscode", "intellij", "eclipse", "library", "api",
			"framework", "module", "service", "function", "class", "object", "method", "interface", "config", "property",
			"ai", "ml", "dl", "model", "training", "inference", "dataset", "embedding", "vector", "feature",
			"classifier", "predict", "anomaly", "detect", "analysis", "분석", "추론", "학습", "평가", "정확도",
			"network", "firewall", "router", "switch", "dns", "ip", "http", "https", "ftp", "smtp",
			"proxy", "tls", "ssl", "vpn", "syslog", "packet", "session", "flow", "port", "endpoint",
			"로그", "로그인", "접속", "차단", "탐지", "정책", "위협", "악성코드", "탐색", "스캔",
			"개인정보", "주민등록번호", "이메일", "전화번호", "주소", "계좌번호", "비밀번호", "파일", "첨부", "문자열",
			"압축파일", "엑셀", "pdf", "word", "hwp", "csv", "이미지", "사진", "기밀", "중요문서"
	);

	private static final Random RANDOM = new Random();

	private EmassDoc.KeywordInfo getKeywordInfo() {
		List<String> pool = new ArrayList<>(KEYWORD_POOL); // ✅ 가변 리스트로 복사
		Collections.shuffle(pool);
		int count = ThreadLocalRandom.current().nextInt(3, 8);
		List<String> selected = pool.subList(0, count);
		EmassDoc.KeywordInfo keywordInfo = new EmassDoc.KeywordInfo();
		keywordInfo.setExist(true);
		keywordInfo.setKeywords(new ArrayList<>(selected)); // ✅ 안전하게 복사
		keywordInfo.setAttachName(List.of(selected.get(0)));
		keywordInfo.setAttach(selected.subList(1, Math.min(3, selected.size())));
		keywordInfo.setBody(selected.subList(Math.max(0, selected.size() - 2), selected.size()));
		return keywordInfo;
	}

	/**
	 * 단일 랜덤 로그 생성
	 */
	private EmassDoc createRandomLog(long id) throws IOException {
		long startTime = Instant.now().minusSeconds(365L * 24 * 60 * 60).toEpochMilli();
		long endTime = System.currentTimeMillis();
		long range = endTime - startTime;
		long timestampMillis = startTime + (id * 1000) % range;
		LocalDateTime timestampDateTime = LocalDateTime.ofInstant(
				Instant.ofEpochMilli(timestampMillis),
				java.time.ZoneId.systemDefault()
		);
		String formattedTime = timestampDateTime.format(FORMATTER);
		EmassDoc doc = new EmassDoc();
		doc.setMsgid(System.currentTimeMillis() + id + "");
		doc.setTimestamp(new Date(timestampMillis));
		doc.setCtime(formattedTime);
		doc.setLtime(formattedTime);
		doc.setDay(getDay(doc));
		doc.setService(getService());
		doc.setNetwork(getNetwork());
		doc.setHttp(getHttp(id));
		doc.setUser(getUser());
		doc.setSize(2323);
		doc.setBody(getBody());
		doc.setAttachCount(1);
		doc.setAttach(getAttach());
		doc.setPrivacyTotal(1);
		doc.setPrivacyInfo(getPrivacyInfo());
		doc.setKeywordInfo(getKeywordInfo());
		return doc;
	}

	//@PostConstruct
	private void init() throws IOException {
		int batchSize = 5000;
		List<EmassDoc> logs = new ArrayList<>();
		long start = System.currentTimeMillis();
		for (long i = 0; i < 200000000; i++) {
			EmassDoc doc = createRandomLog(i);
			logs.add(doc);
			if (i % batchSize == 0) {
				save(logs);
				log.info("save log end : {} | {}", i, DateUtils.duration(start));
				logs.clear();
				start = System.currentTimeMillis();
			}
		}
		if (!logs.isEmpty()) {
			save(logs);
			logs.clear();
		}
	}

	public void bulkInsert(List<EmassDoc> docs) throws IOException {
		BulkRequest bulkRequest = new BulkRequest("emass-202509");
		for (EmassDoc doc : docs) {
			bulkRequest.add(new IndexRequest().id(doc.getMsgid()).source(MAPPER.writeValueAsString(doc), XContentType.JSON));
		}
		BulkResponse response = client.bulk(bulkRequest, RequestOptions.DEFAULT);
		if (response.hasFailures()) {
			log.error("Bulk insert errors: {}", response.buildFailureMessage());
		}
	}

	// 문서 색인
	public EmassDoc save(EmassDoc log) {
		return template.save(log, IndexCoordinates.of("emass-202509"));
	}

	// 문서 색인
	public void save(List<EmassDoc> log) {
		template.save(log, IndexCoordinates.of("emass-202509"));
	}
}
