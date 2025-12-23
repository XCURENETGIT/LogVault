package com.xcurenet.logvault.module.util;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.ExFactory;
import com.xcurenet.logvault.exception.ProcessDataException;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import java.util.Map;

@Log4j2
@Component
@RequiredArgsConstructor
public class CheckWorkingDay {

	private final WorkDayData workingDayData;

	public enum WorkDayType {
		W, // 근무 시간
		R, // 근무 외 시간
		H  // 휴일
	}

	private WorkDayType getWorkingType(final DateTime dt) {
		if (!workingDayData.isWorkDay(dt)) return WorkDayType.H;
		else if (!workingDayData.isWorkingTime(dt)) return WorkDayType.R;
		return WorkDayType.W;
	}

	public void setDay(final ScanData data) {

		if (data.getMsgData() == null) {
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.WORKDAY_MSGDATA_NULL, Map.of("context", "ScanData.msgData is null"));
		}

		DateTime ctime = data.getMsgData().getCtime();
		if (ctime == null) {
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.WORKDAY_CTIME_NULL, Map.of("context", "ScanData.msgData.ctime is null"));
		}

		if (data.getEmassDoc() == null) {
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.WORKDAY_EMASSDOC_NULL, Map.of("context", "EmassDoc is null"));
		}

		try {
			String work = getWorkingType(ctime).name();
			data.getEmassDoc().setDay(EmassDoc.Day.builder().work(work).week(ctime.getWeekOfWeekyear()).build());
		} catch (Exception e) {
			log.warn("{} | {} | msgid={} err={}", ErrorCode.WORKDAY_SET_FAIL, ErrorCode.fromCode(ErrorCode.WORKDAY_SET_FAIL), data.getMsgData().getMsgid(), e.toString());
		}
	}
}
