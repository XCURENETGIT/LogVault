package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.type.HoliDayInfo;
import com.xcurenet.logvault.module.util.HoliDayData;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
		log.info("[INFO_LOAD] HoliDay Size: {}", datas.size());
		data.clear();
		for (HoliDayInfo item : datas) {
			final String cocd = CommonUtil.nvl(item.getCoCd());
			final String busicd = CommonUtil.nvl(item.getBusiCd());
			final String time = CommonUtil.nvl(item.getDate());
			final String comments = CommonUtil.nvl(item.getComments());
			data.put(String.format("%s_%s_%s", cocd, busicd, time), comments);
		}
	}
}
