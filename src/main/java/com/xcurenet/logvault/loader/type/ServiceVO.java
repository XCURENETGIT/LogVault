package com.xcurenet.logvault.loader.type;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.ibatis.type.Alias;
import org.springframework.data.elasticsearch.annotations.Field;

@Data
@Alias("ServiceVO")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceVO {

	@Field("serviceCd")
	@JsonProperty("serviceCd")
	private String serviceCd;

	@Field("serviceName")
	@JsonProperty("serviceName")
	private String serviceName;

	@Field("useYn")
	@JsonProperty("useYn")
	private String useYn;

	@Field("loggingYn")
	@JsonProperty("loggingYn")
	private String loggingYn;

	@Field("companyAccountUseYn")
	@JsonProperty("companyAccountUseYn")
    private String companyAccountUseYn;
}
