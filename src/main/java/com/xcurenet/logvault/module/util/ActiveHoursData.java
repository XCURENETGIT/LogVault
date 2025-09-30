package com.xcurenet.logvault.module.util;

import com.xcurenet.common.utils.CommonUtil;
import lombok.ToString;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@ToString
@Component
public class ActiveHoursData {

	private final Map<String, String> activeHour = new HashMap<>();

	public void put(final String userId, final String aHour) {
		activeHour.put(userId, aHour);
	}

	public void clear() {
		synchronized (this) {
			activeHour.clear();
		}
	}

	public boolean isActiveHours(final String userId, final DateTime dt) {
		String aHour = activeHour.get(userId);

		// 인사기록에 존재하지 않는 값이면 이상행위 판단 안함
		if (CommonUtil.isEmpty(aHour)) return true;

		final int hour = dt.getHourOfDay();
		return '1' == aHour.charAt(hour);
	}
}
