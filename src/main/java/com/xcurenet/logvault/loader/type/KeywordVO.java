package com.xcurenet.logvault.loader.type;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.ibatis.type.Alias;
import org.springframework.data.elasticsearch.annotations.Field;

@Data
@Alias("KeywordVO")
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeywordVO {
	@Field("keywordNm")
	@JsonProperty("keywordName")
	private String keywordNm;

	@Field("minCnt")
	@JsonProperty("minCnt")
	private int minCnt;

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
