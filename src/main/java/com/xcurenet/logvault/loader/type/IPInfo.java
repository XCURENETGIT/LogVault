package com.xcurenet.logvault.loader.type;

import lombok.Data;
import lombok.ToString;
import org.apache.ibatis.type.Alias;
import org.springframework.data.elasticsearch.annotations.Field;

@Alias("IPInfo")
@Data
@ToString
public class IPInfo {
	@Field("USERID")
	private String userId;

	@Field("IP")
	private String ip;
}
