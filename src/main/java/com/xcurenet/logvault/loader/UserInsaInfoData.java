package com.xcurenet.logvault.loader;

import com.xcurenet.common.types.IP;
import com.xcurenet.logvault.loader.type.UserInfo;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Log4j2
@ToString
@Component
public class UserInsaInfoData {
	private final Map<String, UserInfo> mapID = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, UserInfo> mapIP = Collections.synchronizedMap(new HashMap<>());

	public UserInfo getUserByID(final String id) {
		synchronized (mapID) {
			return mapID.get(id.toLowerCase());
		}
	}

	public UserInfo getUserByIP(final IP ip) {
		synchronized (mapIP) {
			return mapIP.get(ip.toHexString());
		}
	}

	public void putUserID(final String userid, final UserInfo userInfo) {
		synchronized (mapID) {
			mapID.put(userid, userInfo);
		}
	}

	public void putIp(final IP ip, final UserInfo userInfo) {
		synchronized (mapIP) {
			mapIP.put(ip.toHexString(), userInfo);
		}
	}

	public void clear() {
		synchronized (this) {
			mapID.clear();
			mapIP.clear();
		}
	}
}
