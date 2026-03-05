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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		long version = mapper.getLastUserInfo();
		List<UserInfo> users = mapper.getUserInfo(version);
		log.info("INFO_LOAD | Rule Version : {} | User Info Size: {}", version, users.size());

		Map<String, UserInfo> newMapID = new HashMap<>();
		Map<String, UserInfo> newMapIP = new HashMap<>();
		for (UserInfo user : users) {
			log.debug("INFO_LOAD | User Info: {}", user);
			if (user.getUserId() != null) {
				newMapID.put(user.getUserId().toLowerCase(), user);
			}

			/*
			 * IP 처리
			 */
			String[] ips = Common.toArray(user.getIp(), ",");
			for (String ipStr : ips) {
				if (ipStr == null || Common.isEmpty(ipStr)) {
					continue;
				}

				try {
					IP ip = new IP(ipStr.trim());
					user.addIp(ip);
					newMapIP.put(ip.toHexString(), user);
					log.debug("INFO_LOAD | IP: {}", ip);
				} catch (IOException e) {
					log.warn("INFO_LOAD | ip error: user:{}, input:{} message:{}", user.getName(), ipStr, e.getMessage());
				}
			}

			/*
			 * EMAIL 처리
			 */
			String[] emails = Common.toArray(user.getEmail(), ",");
			for (String emailStr : emails) {
				if (emailStr == null || Common.isEmpty(emailStr)) {
					continue;
				}
				user.addEmail(emailStr);
			}
		}
		data.replaceAll(newMapID, newMapIP);
		log.info("INFO_LOAD COMPLETE | idSize={} ipSize={}", newMapID.size(), newMapIP.size());
	}
}