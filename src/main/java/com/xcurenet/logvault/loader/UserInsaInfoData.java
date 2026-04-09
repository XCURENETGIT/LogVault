package com.xcurenet.logvault.loader;

import com.xcurenet.common.types.IP;
import com.xcurenet.logvault.loader.type.UserInfo;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@ToString
@Component
public class UserInsaInfoData {

	private volatile Map<String, UserInfo> mapID = new ConcurrentHashMap<>();
	private volatile Map<String, UserInfo> mapIP = new ConcurrentHashMap<>();
	private volatile Map<Integer, UserInfo> mapPort = new ConcurrentHashMap<>();

	public UserInfo getUserByID(final String id) {
		if (id == null) return null;
		return mapID.get(id.toLowerCase());
	}

	public UserInfo getUserByIP(final IP ip) {
		if (ip == null) return null;
		return mapIP.get(ip.toHexString());
	}

	public UserInfo getUserByPort(final Integer port) {
		if (port == null) return null;
		return mapPort.get(port);
	}

	public void putUserID(final String userid, final UserInfo userInfo) {
		if (userid == null || userInfo == null) return;
		mapID.put(userid.toLowerCase(), userInfo);
	}

	public void putIp(final IP ip, final UserInfo userInfo) {
		if (ip == null || userInfo == null) return;
		mapIP.put(ip.toHexString(), userInfo);
	}

	/**
	 * 인사정보 전체 갱신 (10분 갱신용)
	 */
	public void replaceAll(Map<String, UserInfo> newMapID, Map<String, UserInfo> newMapIP, Map<Integer, UserInfo> newMapPort) {

		if (newMapID == null && newMapIP == null && newMapPort == null) {
			log.warn("replaceAll called with null map");
			return;
		}

		this.mapID = new ConcurrentHashMap<>(newMapID);
		this.mapIP = new ConcurrentHashMap<>(newMapIP);
		this.mapPort = new ConcurrentHashMap<>(newMapPort);
		log.info("UserInsaInfoData refreshed. sizeID={}, sizeIP={}, sizePort={}", mapID.size(), mapIP.size(), newMapPort.size());
	}
}