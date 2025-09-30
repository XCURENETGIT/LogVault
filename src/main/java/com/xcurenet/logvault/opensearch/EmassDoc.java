package com.xcurenet.logvault.opensearch;

import lombok.Data;
import org.opensearch.common.geo.GeoPoint;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

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
	private int attachTotalSize;

	@Field("attach_count")
	private int attachCount;

	@Field("attach")
	private List<Attach> attach;

	@Field("privacy_total")
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
		@Field("hash")
		private String hash;
		@Field("size")
		private Long size;
		@Field("text")
		private String text;
	}

	@Data
	public static class PrivacyInfo {
		@Field("id")
		private String id;
		@Field("type")
		private String type;
		@Field("attach_name")
		private String attachName;
		@Field("keywords")
		private List<String> keywords;
		@Field("count")
		private int count;
	}

	@Data
	public static class KeywordInfo {
		@Field("exist")
		private boolean exist;
		@Field("keywords")
		private List<String> keywords;
		@Field("attach")
		private List<String> attach;
		@Field("attach_name")
		private List<String> attachName;
		@Field("body")
		private List<String> body;
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
}
