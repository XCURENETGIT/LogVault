package com.xcurenet.logvault.loader.type;

import lombok.Data;
import org.apache.ibatis.type.Alias;

@Data
@Alias("ConfVO")
public class ConfVO {
	private String confId;
	private String val;
}
