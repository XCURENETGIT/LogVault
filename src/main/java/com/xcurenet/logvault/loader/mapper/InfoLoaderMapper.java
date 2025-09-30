package com.xcurenet.logvault.loader.mapper;

import com.xcurenet.logvault.loader.type.IPInfo;
import com.xcurenet.logvault.loader.type.UserInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface InfoLoaderMapper {
	List<UserInfo> getUserInfo();

	List<IPInfo> getUserIp();
}
