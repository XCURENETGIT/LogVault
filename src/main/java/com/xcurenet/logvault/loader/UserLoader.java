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
import java.util.function.Consumer;

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
		Map<Integer, UserInfo> newMapPort = new HashMap<>();
		for (UserInfo user : users) {
			log.debug("INFO_LOAD | User Info: {}", user);
			if (user.getUserId() != null) {
				newMapID.put(user.getUserId().toLowerCase(), user);
			}

			loadIps(user, newMapIP);
			loadPorts(user, newMapPort);
			loadEmails(user);
		}
		data.replaceAll(newMapID, newMapIP, newMapPort);
		log.info("INFO_LOAD COMPLETE | idSize={} ipSize={} portSize={}", newMapID.size(), newMapIP.size(), newMapPort.size());
	}

	private void loadIps(final UserInfo user, final Map<String, UserInfo> newMapIP) {
		forEachToken(user.getIp(), ipStr -> {
			try {
				IP ip = new IP(ipStr);
				user.addIp(ip);
				newMapIP.put(ip.toHexString(), user);
				log.debug("INFO_LOAD | IP: {}", ip);
			} catch (IOException e) {
				log.warn("INFO_LOAD | ip error: user:{}, input:{} message:{}", user.getName(), ipStr, e.getMessage());
			}
		});
	}

	private void loadPorts(final UserInfo user, final Map<Integer, UserInfo> newMapPort) {
		forEachToken(user.getPort(), portStr -> {
			int port = Common.nvz(portStr);
			user.addPort(port);
			newMapPort.put(port, user);
			log.debug("INFO_LOAD | PORT: {}", port);
		});
	}

	private void loadEmails(final UserInfo user) {
		forEachToken(user.getEmail(), user::addEmail);
	}

	private void forEachToken(final String value, final Consumer<String> action) {
		String[] tokens = Common.toArray(value, ",");
		for (String token : tokens) {
			if (token == null || Common.isEmpty(token)) {
				continue;
			}
			action.accept(token.trim());
		}
	}
}
