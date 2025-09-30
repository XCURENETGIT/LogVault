package com.xcurenet.logvault.loader.type;

import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Field;

@Data
public class WorkDayInfo {

	@Field("COCD")
	private String coCd;

	@Field("BUSICD")
	private String busiCd;

	@Field("WDAY")
	private String wDay;

	@Field("WHOUR")
	private String wHour;
}
