package com.xcurenet.logvault.loader.type;

import lombok.Data;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

@Data
@Alias("HoliDayInfo")
@ToString
public class HoliDayInfo implements Serializable {

	private final String coCd;

	private final String busiCd;

	private final String date;

	private final String comments;
}
