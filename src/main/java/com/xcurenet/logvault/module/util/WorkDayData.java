package com.xcurenet.logvault.module.util;

import lombok.ToString;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ToString
@Component
public class WorkDayData {

	private final Map<String, WorkTime> workday = new ConcurrentHashMap<>();

	public boolean isWorkingTime(final DateTime dt) {
		final WorkTime work = workday.get("workTime");
		return work != null && work.isWorkingTime(dt);
	}

	public boolean isWorkDay(final DateTime dt) {
		final WorkTime work = workday.get("workTime");
		return work != null && work.isWorkDay(dt);
	}

	public void put(final String key, final WorkTime val) {
		workday.put(key, val);
	}

	public void clear() {
		workday.clear();
	}

	/**
	 * @param wday sunday is first
	 */
	public record WorkTime(String wday, String whour) {

		public boolean isWorkingTime(final DateTime dt) {
			// dt = 2024-03-19T18:29:23.000+09:00
			final int hour = dt.getHourOfDay();
			return isWorkDay(dt) && '1' == whour.charAt(hour);
		}

		public boolean isWorkDay(final DateTime dt) {
			final int day = dt.getDayOfWeek() - 1;
			return '1' == wday.charAt(day);
		}
	}

	public static void main(String[] args) {
		DateTime dt = new DateTime("2025-12-29T12:29:23.000+09:00");
		WorkDayData workDayData = new WorkDayData();
		workDayData.put("workTime", new WorkTime("1011110", "000000000111111111110000"));
		System.out.println(workDayData.isWorkingTime(dt));
		System.out.println(workDayData.isWorkDay(dt));
	}
}
