package com.xcurenet.logvault.module.alert;

import lombok.Data;
import org.apache.ibatis.type.Alias;

@Data
@Alias("AlertMessage")
public class AlertMessage {
	private String msgId;
	private String data;
}
