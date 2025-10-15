package com.xcurenet.logvault.module.util;

import lombok.ToString;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@ToString
@Component
public class WorkDayData {

	private final Map<String, WorkTime> workday = new HashMap<>();

	public boolean isWorkingTime(final String cocd, final String busicd, final DateTime dt) {
		final String key = String.format("%s_%s", cocd, busicd);
		final WorkTime work = workday.get(key);
		return work != null && work.isWorkingTime(dt);
	}

	public void put(final String key, final WorkTime val) {
		workday.put(key, val);
	}

	public void clear() {
		synchronized (this) {
			workday.clear();
		}
	}

	/**
	 * @param wday sunday is first
	 */
	public record WorkTime(String wday, String whour) {

		public boolean isWorkingTime(final DateTime dt) {
			// joda-time의 요일은 1-7이고 월요일이 첫번째다. 그렇기 때문에 0-6, 일요일을 첫번째으로 변경
			// dt = 2024-03-19T18:29:23.000+09:00
			final int day = dt.getDayOfWeek() % 7;
			final int hour = dt.getHourOfDay();
			return '1' == wday.charAt(day) && '1' == whour.charAt(hour);
		}
	}
}
