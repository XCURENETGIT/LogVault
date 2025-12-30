package com.xcurenet.logvault.loader.type;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;
import org.apache.ibatis.type.Alias;
import org.springframework.data.elasticsearch.annotations.Field;

@Data
@ToString
@Alias("PatternInfo")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatternInfo {
	@Field("patternCd")
	@JsonProperty("PATTERN_CD")
	private String patternCd;

	@Field("patternNm")
	@JsonProperty("PATTERN_NM")
	private String patternNm;

	@Field("patternType")
	@JsonProperty("PATTERN_TYPE")
	private String patternType;

	@Field("regex")
	@JsonProperty("REGEX")
	private String regex;

	@Field("minCount")
	@JsonProperty("MIN_CNT")
	private int minCount;

	@Field("alarmYn")
	@JsonProperty("ALARM_YN")
	private String alarmYn;

	@Field("syslogYn")
	@JsonProperty("SYSLOG_YN")
	private String syslogYn;

	@Field("useYn")
	@JsonProperty("USE_YN")
	private String useYn;
}
