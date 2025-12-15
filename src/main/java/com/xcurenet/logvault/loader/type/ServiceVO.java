package com.xcurenet.logvault.loader.type;

import lombok.Data;
import org.apache.ibatis.type.Alias;
import org.springframework.data.elasticsearch.annotations.Field;

@Data
@Alias("ServiceVO")
public class ServiceVO {
	@Field("serviceCd")
	private String serviceCd;
	@Field("serviceName")
	private String serviceName;
}
