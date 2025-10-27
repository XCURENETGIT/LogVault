package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.type.WorkDayInfo;
import com.xcurenet.logvault.module.util.WorkDayData;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class WorkDayLoader {

	private final InfoLoaderMapper mapper;

	@Getter
	private final WorkDayData workday;

	public void load() {
		List<WorkDayInfo> datas = mapper.getWorkDay();
		log.info("[INFO_LOAD] WorkDay Size: {}", datas.size());
		workday.clear();
		for (WorkDayInfo item : datas) {
			final String cocd = Common.nvl(item.getCoCd());
			final String busicd = Common.nvl(item.getBusiCd());
			final String wday = Common.nvl(item.getWDay());
			final String whour = Common.nvl(item.getWHour());
			workday.put(String.format("%s_%s", cocd, busicd), new WorkDayData.WorkTime(wday, whour));
		}
	}
}
