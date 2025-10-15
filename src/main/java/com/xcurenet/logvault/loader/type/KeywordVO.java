package com.xcurenet.logvault.loader.type;

import lombok.Data;
import org.apache.ibatis.type.Alias;

@Data
@Alias("KeywordVO")
public class KeywordVO {
	private String keywordNm;
	private int minCnt;
}
