package com.xcurenet.logvault.loader.type;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Field;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountVO {
	@Field("companyAccount")
	@JsonProperty("companyAccount")
	private String companyAccount;

	@Field("serviceCd")
	@JsonProperty("serviceCd")
	private String serviceCd;

	@Field("matchType")
	@JsonProperty("matchType")
	private String matchType;

}
