package com.xcurenet.logvault.loader;

import com.xcurenet.common.types.IP;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.type.UserInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserLoader {
	private final InfoLoaderMapper mapper;

	@Getter
	private final UserInsaInfoData data;

	public void load() {
		loadUser();
	}

	public void loadUser() {
		List<UserInfo> users = mapper.getUserInfo();
		log.info("[INFO_LOAD] User Info Size: {}", users.size());
		data.clear();
		for (UserInfo user : users) {
			data.putUserID(user.getUserId(), user);
			log.debug("[INFO_LOAD] User Info: {}", user);
			String[] ips = Common.toArray(user.getIp(), ",");
			for (String ipStr : ips) {
				if (ipStr == null || Common.isEmpty(ipStr)) continue; //사용자가 없는 IP 혹은 IP 정보가 없으면 무시
				try {
					IP ip = new IP(ipStr.trim());
					user.addIp(ip);
					data.putIp(ip, user);
					log.debug("[INFO_LOAD] IP: {}", ip);
				} catch (IOException e) {
					log.warn("ip error: user:{}, input:{} message:{}", user.getName(), ipStr, e.getMessage());
				}
			}
		}
	}
}
