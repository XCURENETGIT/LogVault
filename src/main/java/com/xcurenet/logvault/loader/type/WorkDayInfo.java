package com.xcurenet.logvault.loader.type;

import lombok.Data;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

@Data
@Alias("WorkDayInfo")
@ToString
public class WorkDayInfo implements Serializable {

	private String coCd;

	private String busiCd;

	private String wDay;

	private String wHour;
}
