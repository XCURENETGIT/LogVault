package com.xcurenet.logvault.loader.mapper;

import com.xcurenet.logvault.loader.type.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface InfoLoaderMapper {
	List<UserInfo> getUserInfo();

	List<IPInfo> getUserIp();

	List<HoliDayInfo> getHoliDay();

	List<WorkDayInfo> getWorkDay();

	List<KeywordVO> getKeyword();

	List<PatternInfo> getPatternInfo();

	List<ConfVO> getUIConf();
}
