package com.xcurenet.logvault.opensearch;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xcurenet.logvault.module.util.ActionType;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@Document(indexName = "emass", writeTypeHint = WriteTypeHint.FALSE)
public class EmassDoc {
	@Id
	@Field("msgid")
	private String msgid;

	@Field("action")
	private ActionType action;

	@Field("test_message")
	private boolean testMessage = false;

	@Field("room_id")
	private String roomId;

	@Field("root_mtr")
	private String rootMtr;

	@Field("parent_mtr")
	private String parentMtr;

	@Field(name = "@timestamp", type = FieldType.Date, format = DateFormat.epoch_millis)
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
	private Date timestamp;

	@Field("ctime")
	private String ctime;

	@Field("ltime")
	private String ltime;

	@Field("day")
	private Day day;

	@Field("process_status")
	private ProcessStatus processStatus;

	@Field("ml_result")
	private MLResult mlResult;

	@Field("service")
	private Service service;

	@Field("network")
	private Network network;

	@Field("http")
	private Http http;

	@Field("user")
	private User user;

	@Field("size")
	private long size;

	@Field("body")
	private Body body;

	@Field("attach_total_size")
	private long attachTotalSize;

	@Field("attach_count")
	private int attachCount;

	@Field("attach_exist_count")
	private int attachExistCount;

	@Field("attach")
	private List<Attach> attach;

	@Field("privacy_total") //탐지 개인정보 총 건수
	private int privacyTotal;

	@Field("privacy_info")
	private List<PrivacyInfo> privacyInfo;

	@Field("keyword_total") //탐지 키워드 총 건수
	private int keywordTotal;

	@Field("keyword_info")
	private KeywordInfo keywordInfo;

	@Field("anomaly_score")
	private AnomalyScore anomalyScore;

    @Field("rule_seq") // 정책 번호
    private Integer ruleSeq;

    @Field("rule_name") // 정책 명
    private String ruleName;

    @Field("block_extension") // 차단 파일 확장자
    private String blockExtension;

	@Data
	@Builder
	public static class Day {
		@Field("week")
		private int week;
		@Field("work")
		private String work;
	}

	@Data
	@Builder
	public static class ProcessStatus {
		@Field("ocr")
		private String ocr;
		@Field("ml")
		private String ml;
	}

	@Data
	public static class MLResult {
		@Field("code_exist")
		private boolean codeExist;
		@Field("category")
		private int category;
		@Field("probs")
		private float probs;
		@Field("keywords")
		private List<String> keywords;

		@Field("similarity_exist")
		private boolean similarityExist;
		@Field("similarity_id")
		private String similarityId;
		@Field("similarity_name")
		private String similarityName;
		@Field("similarity_score")
		private float similarityScore;

		@Field("result")
		private int result;
		@Field("message")
		private String message;

		public void merge(MLResult other) {
			if (other == null) {
				return;
			}
			this.codeExist |= other.isCodeExist();
			this.category = Math.max(this.category, other.getCategory());
			this.probs = Math.max(this.probs, other.getProbs());
			if (other.getKeywords() != null && !other.getKeywords().isEmpty()) {
				if (this.keywords == null) this.keywords = new ArrayList<>();
				this.keywords.addAll(other.getKeywords());
			}
			this.similarityExist |= other.isSimilarityExist();
			if (other.getSimilarityId() != null) this.similarityId = other.getSimilarityId();
			if (other.getSimilarityName() != null) this.similarityName = other.getSimilarityName();
			this.similarityScore = Math.max(this.similarityScore, other.getSimilarityScore());
			if (other.getResult() > 0 && (this.result <= 0 || other.getResult() > this.result))
				this.result = other.getResult();
			if (other.getMessage() != null && !other.getMessage().isBlank()) this.message = other.getMessage();
		}
	}

	@Data
	public static class Service {
		@Field("svc")
		private String svc;
		@Field("svc1")
		private String svc1;
		@Field("svc2")
		private String svc2;
		@Field("svc3")
		private String svc3;
		@Field("svc4")
		private String svc4;
		@Field("svc12")
		private String svc12;
	}

	@Data
	public static class Network {
		@Field("protocol")
		private String protocol;

		@Field("src_port")
		private int srcPort;
		@Field("src_ip")
		private String srcIp;

		@Field("dst_port")
		private int dstPort;
		@Field("dst_ip")
		private String dstIp;
	}

	@Data
	public static class Http {
		@Field("url")
		private String url;
		@Field("header")
		private Header header;
		@Field("user_agent")
		private Agent agent;
	}

	@Data
	public static class User {
		@Field("ip")
		private String ip;

		@Field("proxy_port")
		private int proxyPort;

		@Field("id")
		private String id;

		@Field("name")
		private String name;

		@Field("is_ceo")
		@JSONField(name = "is_ceo")
		@JsonProperty("is_ceo")
		private boolean ceo;

		@Field("dept_code")
		@JSONField(name = "dept_code")
		@JsonProperty("dept_code")
		private String deptCode;

		@Field("dept_name")
		@JSONField(name = "dept_name")
		@JsonProperty("dept_name")
		private String deptName;

		@Field("jikgub_code")
		@JSONField(name = "jikgub_code")
		@JsonProperty("jikgub_code")
		private String jikgubCode;

		@Field("jikgub_name")
		@JSONField(name = "jikgub_name") //직렬화 용도
		@JsonProperty("jikgub_name") //역직렬화 용도
		private String jikgubName;
	}

	@Data
	public static class Body {
		@Field("size")
		private long size;
		@Field("path")
		private String path;
		@Field("extension")
		private String extension;
		@Field("text")
		private String text;
		@Field("guardrail_category")
		private String guardrailCategory;
		@Field("ml_result")
		private MLResult mlResult;
	}

	@Data
	public static class Attach {
		@Field("id")
		private String id;
		@Field("name")
		private String name;
		@Field("has_name")
		private boolean hasName;
		@Field("extension")
		private String extension;
		@Field("expected_extension")
		private String expectedExtension;
		@Field("expected_unknown")
		private boolean expectedUnknown;
		@Field("change_extension")
		private boolean changeExtension;
		@Field("encrypted")
		private boolean encrypted;
		@Field("hash")
		private String hash;
		@Field("exist")
		private boolean exist;
		@Field("size")
		private Long size;
		@Field("ocr_target")
		private boolean ocrTarget;
		@Field("ocr_status")
		private String ocrStatus;
		@Field("ocr_rate")
		private Long ocrRate;
		@Field("path")
		private String path;
		@Field("text")
		private String text;

		@Field("guardrail_category")
		private String guardrailCategory;

		@Field("image_extractor_info")
		private List<ImageExtractorInfo> imageExtractorInfo;

		@Field("sheet_info")
		private SheetInfo sheetInfo;

		@Field("ml_result")
		private MLResult mlResult;

		@Transient
		private String srcPath;
	}

	@Data
	public static class PrivacyInfo {
		@Field("id") //SN:주민번호, CN:카드번호
		private String id;
		@Field("type") //B:본문, A:첨부
		private String type;
		@Field("attach_name")
		private String attachName;
		@Field("privacy_data") //탐지 키워드 정보
		private List<String> privacyData;
		@Field("count")
		private int count;
	}

	@Data
	public static class KeywordInfo {
		@Field("exist")
		private boolean exist;
		@Field("keywords")
		private List<Keyword> keywords;
		@Field("attach")
		private List<Keyword> attach;
		@Field("attach_name")
		private List<Keyword> attachName;
		@Field("body")
		private List<Keyword> body;

		@Data
		@Builder
		public static class Keyword {
			@Field("name")
			private String name;
			@Field("count")
			private int count;
		}
	}

	@Data
	public static class Agent {
		@Field("raw")
		private String raw;

		@Field("device") //iPhone
		private String device;

		@Field("os") //iOS
		private String os;
		@Field("os_version") //5.1
		private String osVersion;

		@Field("client") //Mobile Safari
		private String client;
		@Field("client_version") //3.4
		private String clientVersion;
	}

	@Data
	@Builder
	public static class Header {
		@Field("request")
		private RequestHeader request;
		@Field("response")
		private ResponseHeader response;

		@Data
		@Builder
		public static class RequestHeader {
			@Field("method")
			private String method;
			@Field("protocol")
			private String protocol;
			@Field("origin")
			private String origin;
		}

		@Data
		@Builder
		public static class ResponseHeader {
			@Field(name = "date")
			private String date;
			@Field("content-type")
			private String contentType;
		}
	}

	@Data
	@Builder
	public static class ImageExtractorInfo {
		@Field("name")
		private String name;
		@Field("path")
		private String path;
	}

	@Data
	public static class SheetInfo {
		@Field("sheet_total")
		private int sheetTotal;
		@Field("sheet_hidden_total")
		private int sheetHiddenTotal;
		@Field("hidden_sheet_names")
		private List<String> hiddenSheetNames;
	}

	@Data
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class AnomalyScore {
		@Field("guardrail")
		private ScoreEntry guardrail = new ScoreEntry();
		@Field("keyword")
		private ScoreEntry keyword = new ScoreEntry();
		@Field("pattern")
		private ScoreEntry pattern = new ScoreEntry();
		@Field("code_exist")
		private ScoreEntry codeExist = new ScoreEntry();
		@Field("similarity")
		private ScoreEntry similarity = new ScoreEntry();
		@Field("attach")
		private ScoreEntry attach = new ScoreEntry();
		@Field("total")
		private ScoreEntry total = new ScoreEntry();

		public void calculateTotal() {
			this.total.setScore(guardrail.getScore() + keyword.getScore() + pattern.getScore()
					+ codeExist.getScore() + similarity.getScore() + attach.getScore());
			this.total.setCount(guardrail.getCount() + keyword.getCount() + pattern.getCount()
					+ codeExist.getCount() + similarity.getCount() + attach.getCount());
			// score/count 가 모두 0인 항목은 null 처리하여 인덱싱에서 제외
			guardrail = nullIfEmpty(guardrail);
			keyword = nullIfEmpty(keyword);
			pattern = nullIfEmpty(pattern);
			codeExist = nullIfEmpty(codeExist);
			similarity = nullIfEmpty(similarity);
			attach = nullIfEmpty(attach);
		}

		private static ScoreEntry nullIfEmpty(ScoreEntry entry) {
			return (entry == null || (entry.getScore() == 0 && entry.getCount() == 0)) ? null : entry;
		}

		@Data
		public static class ScoreEntry {
			@Field("score")
			private int score;
			@Field("count")
			private int count;

			public void add(int scoreValue) {
				if (scoreValue > 0) {
					this.score += scoreValue;
					this.count++;
				}
			}
		}
	}
}
