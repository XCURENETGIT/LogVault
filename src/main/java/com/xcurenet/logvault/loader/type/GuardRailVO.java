package com.xcurenet.logvault.loader.type;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.ibatis.type.Alias;
import org.springframework.data.elasticsearch.annotations.Field;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GuardRailVO {
	@Field("guardRailCd")
	private String guardRailCd;

	@Field("useYn")
	private String useYn;

	@Field("guardRailOrder")
	private int guardRailOrder;

}
