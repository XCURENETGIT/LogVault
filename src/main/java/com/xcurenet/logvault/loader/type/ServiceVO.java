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
	@JsonProperty("SERVICE_CD")
	private String serviceCd;

	@Field("serviceName")
	@JsonProperty("SERVICE_NAME")
	private String serviceName;

	@Field("useYn")
	@JsonProperty("USE_YN")
	private String useYn;

	@Field("loggingYn")
	@JsonProperty("LOGGING_YN")
	private String loggingYn;

}
