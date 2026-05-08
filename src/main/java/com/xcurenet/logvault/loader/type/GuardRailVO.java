package com.xcurenet.logvault.loader.type;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Field;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GuardRailVO {
	@Field("guardRailCd")
	@JsonProperty("guardRailCd")
	private String guardRailCd;

	@Field("useYn")
	@JsonProperty("useYn")
	private String useYn;

	@Field("guardRailOrder")
	@JsonProperty("guardRailOrder")
	private int guardRailOrder;

}
