package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.type.HoliDayInfo;
import com.xcurenet.logvault.module.util.HoliDayData;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

@Getter
@Log4j2
@Service
@RequiredArgsConstructor
public class HoliDayLoader {

	private final InfoLoaderMapper mapper;

	@Getter
	private final HoliDayData data;

	public void load() {
		List<HoliDayInfo> datas = mapper.getHoliDay();
		log.info("INFO_LOAD | HoliDay Size: {}", datas.size());
		data.clear();
		for (HoliDayInfo item : datas) {
			final String cocd = Common.nvl(item.coCd());
			final String busicd = Common.nvl(item.busiCd());
			final String time = Common.nvl(item.date());
			final String comments = Common.nvl(item.comments());
			data.put(String.format("%s_%s_%s", cocd, busicd, time), comments);
		}
	}
}
