package com.xcurenet.logvault.opensearch;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.geo.GeoPoint;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

@Data
@Document(indexName = "emass", writeTypeHint = WriteTypeHint.FALSE)
public class EmassDoc {
	@Id
	@Field("msgid")
	private String msgid;

	@Field(name = "@timestamp", type = FieldType.Date, format = DateFormat.epoch_millis)
	private Date timestamp;

	@Field("ctime")
	private String ctime;

	@Field("ltime")
	private String ltime;

	@Field("day")
	private Day day;

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

	@Field("keyword_info")
	private KeywordInfo keywordInfo;

	@Data
	public static class Day {
		@Field("week")
		private int week;

		@Field("work")
		private String work;
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
		@Field("src_asn")
		private String srcAsn;
		@Field("src_country")
		private String srcCountry;
		@Field("src_location")
		private GeoPoint srcLocation;

		@Field("dst_port")
		private int dstPort;
		@Field("dst_ip")
		private String dstIp;
		@Field("dst_asn")
		private String dstAsn;
		@Field("dst_country")
		private String dstCountry;
		@Field("dst_location")
		private GeoPoint dstLocation;
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
		@Field("id")
		private String id;
		@Field("name")
		private String name;
		@Field("is_ceo")
		private boolean ceo;
		@Field("dept_code")
		private String deptCode;
		@Field("dept_name")
		private String deptName;
		@Field("jikgub_code")
		private String jikgubCode;
		@Field("jikgub_name")
		private String jikgubName;
	}

	@Data
	public static class Body {
		@Field("size")
		private long size;
		@Field("language")
		private String language;
		@Field("text")
		private String text;
	}

	@Data
	public static class Attach {
		@Field("id")
		private String id;
		@Field("name")
		private String name;
		@Field("has_name")
		private boolean hasName;
		@Field("expected_extension")
		private String expectedExtension;
		@Field("expected_unknown")
		private boolean expectedUnknown;
		@Field("hash")
		private String hash;
		@Field("exist")
		private boolean exist;
		@Field("size")
		private Long size;
		@Field("base64")
		private String base64;
		@Field("text")
		private String text;
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
		private List<PrivacyData> privacyData;

		@Field("count")
		private int count;
	}

	@Data
	@Builder
	public static class PrivacyData {
		@Field("start")
		private int start;
		@Field("end")
		private int end;
		@Field("match")
		private String match;
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
			@Field(name = "date", type = FieldType.Date)
			private ZonedDateTime date;

			@Field("content-type")
			private String contentType;
		}
	}
}
