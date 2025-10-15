package com.xcurenet.logvault.loader.type;

import lombok.Data;
import lombok.ToString;
import org.apache.ibatis.type.Alias;
import org.springframework.data.elasticsearch.annotations.Field;

@Alias("PatternInfo")
@Data
@ToString
public class PatternInfo {
	@Field("patternCd")
	private String patternCd;

	@Field("patternNm")
	private String patternNm;

	@Field("patternType")
	private String patternType;

	@Field("regex")
	private String regex;

	@Field("minCount")
	private int minCount;
}
