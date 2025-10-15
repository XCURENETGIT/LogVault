package com.xcurenet.logvault.loader.type;

import org.apache.ibatis.type.Alias;

import java.io.Serializable;

@Alias("HoliDayInfo")
public record HoliDayInfo(String coCd, String busiCd, String date, String comments) implements Serializable {
}
