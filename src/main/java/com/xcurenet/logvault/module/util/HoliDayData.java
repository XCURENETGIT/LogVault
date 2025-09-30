package com.xcurenet.logvault.module.util;

import lombok.Data;
import lombok.ToString;
import org.joda.time.DateTime;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@ToString
@Component
public class HoliDayData {

	private final Map<String, String> holiday = new HashMap<>();

	@Data
	public static class HoliDayInfo {

		@Field("COCD")
		private final String coCd;

		@Field("BUSICD")
		private final String busiCd;

		@Field("DATE")
		private final String date;

		@Field("COMMENTS")
		private final String comments;
	}

	public boolean isHoliDay(final String cocd, final String busicd, final DateTime date) {
		return isHoliDay(cocd, busicd, date.toString("yyyy-MM-dd"));
	}

	public boolean isHoliDay(final String cocd, final String busicd, final String date) {
		final String key = String.format("%s_%s_%s", cocd, busicd, date);
		return holiday.containsKey(key);
	}

	public String getHoliDay(final String cocd, final String busicd, final DateTime date) {
		return getHoliDay(cocd, busicd, date.toString("yyyy-MM-dd"));
	}

	public String getHoliDay(final String cocd, final String busicd, final String date) {
		final String key = String.format("%s_%s_%s", cocd, busicd, date);
		return holiday.get(key);
	}

	public void put(final String key, final String val) {
		holiday.put(key, val);
	}

	public void clear() {
		synchronized (this) {
			holiday.clear();
		}
	}
}
