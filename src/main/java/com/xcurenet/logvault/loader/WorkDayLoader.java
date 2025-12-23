package com.xcurenet.logvault.loader;

import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.type.WorkDayInfo;
import com.xcurenet.logvault.module.util.WorkDayData;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class WorkDayLoader {

	private final InfoLoaderMapper mapper;

	@Getter
	private final WorkDayData workday;

	public void load() {
		WorkDayInfo workDayInfo = mapper.getWorkDay();
		log.info("INFO_LOAD | WorkDay : {}", workDayInfo);
		workday.clear();

		if (workDayInfo != null) {
			workday.put("workTime", new WorkDayData.WorkTime(workDayInfo.getWDay(), workDayInfo.getWHour()));
		}
	}
}
