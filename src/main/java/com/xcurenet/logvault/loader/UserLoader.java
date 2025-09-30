package com.xcurenet.logvault.loader;

import com.xcurenet.common.types.IP;
import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.logvault.database.DBCommon;
import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.type.IPInfo;
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

	private final DBCommon dbCommon;

	@Getter
	private final UserInsaInfoData data;

	public void load() {
		loadUser();
		loadIp();
	}

	public void loadUser() {
		List<UserInfo> users = mapper.getUserInfo();
		log.info("[INFO_LOAD] User Info Size: {}", users.size());
		data.clear();
		for (UserInfo user : users) {
			data.putUserID(user.getUserId(), user);
		}
	}

	public void loadIp() {
		List<IPInfo> ips = mapper.getUserIp();
		log.info("[INFO_LOAD] User IP Size: {}", ips.size());

		for (IPInfo ipInfo : ips) {
			String userId = CommonUtil.nvl(ipInfo.getUserId()).toLowerCase();
			final String ipStr = CommonUtil.nvl(ipInfo.getIp());
			final UserInfo user = data.getUserByID(userId);
			if (user == null || CommonUtil.isEmpty(ipStr)) continue; //사용자가 없는 IP 혹은 IP 정보가 없으면 무시

			try {
				IP ip = new IP(ipStr);
				user.addIp(ip);
				data.putIp(ip, user);
			} catch (IOException e) {
				log.warn("ip error: user:{}, input:{} message:{}", user.getName(), ipStr, e.getMessage());
			}
		}
	}
}
