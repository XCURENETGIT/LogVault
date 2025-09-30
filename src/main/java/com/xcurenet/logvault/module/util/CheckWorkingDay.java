package com.xcurenet.logvault.module.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class CheckWorkingDay {

	private final HoliDayData holiDayData;

	private final WorkDayData workingDayData;

	private final ActiveHoursData activeHoursData;

	public enum WorkDayType {
		W, // W : 근무 시간
		R, // R : 근무 외 시간
		H // H : 휴일
	}

	public WorkDayType getWorkingType(final String cocd, final String busicd, final DateTime dt) {
		if (cocd == null || busicd == null) return WorkDayType.W;
		else if (holiDayData.isHoliDay("00000", "00000", dt)) return WorkDayType.H; // 국경일 여부 확인
		else if (holiDayData.isHoliDay(cocd, busicd, dt)) return WorkDayType.H;                 // 회사 지정 휴일 여부 확인
		else if (!workingDayData.isWorkingTime(cocd, busicd, dt)) return WorkDayType.R;         // 근무 시간 여부 확인
		return WorkDayType.W;
	}

	public boolean isActiveHours(final String userId, final DateTime dt) {
		return activeHoursData.isActiveHours(userId, dt);
	}
}
