package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.logvault.database.DBCommon;
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

	private final DBCommon dbCommon;

	@Getter
	private final WorkDayData workday;

	public void load() {
		List<WorkDayInfo> datas = dbCommon.getInfoToCollection("INFO_WORKDAY", WorkDayInfo.class);
		log.info("[INFO_LOAD] WorkDay Size: {}", datas.size());
		workday.clear();
		for (WorkDayInfo item : datas) {
			final String cocd = CommonUtil.nvl(item.getCoCd());
			final String busicd = CommonUtil.nvl(item.getBusiCd());
			final String wday = CommonUtil.nvl(item.getWDay());
			final String whour = CommonUtil.nvl(item.getWHour());
			workday.put(String.format("%s_%s", cocd, busicd), new WorkDayData.WorkTime(wday, whour));
		}
	}
}
