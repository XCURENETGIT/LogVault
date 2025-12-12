package com.xcurenet.logvault.loader.type;

import lombok.Data;
import org.apache.ibatis.type.Alias;
import org.springframework.data.elasticsearch.annotations.Field;

@Data
@Alias("KeywordVO")
public class KeywordVO {
	@Field("keywordNm")
	private String keywordNm;

	@Field("minCnt")
	private int minCnt;

	@Field("alarmYn")
	private String alarmYn;

	@Field("syslogYn")
	private String syslogYn;
}
