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
	@JsonProperty("patternCd")
	private String patternCd;

	@Field("patternNm")
	@JsonProperty("patternName")
	private String patternNm;

	@Field("patternType")
	@JsonProperty("patternType")
	private String patternType;

	@Field("regex")
	@JsonProperty("regex")
	private String regex;

	@Field("minCount")
	@JsonProperty("minCnt")
	private int minCount;

	@Field("alarmYn")
	@JsonProperty("alarmYn")
	private String alarmYn;

	@Field("syslogYn")
	@JsonProperty("syslogYn")
	private String syslogYn;

	@Field("useYn")
	@JsonProperty("useYn")
	private String useYn;
}
