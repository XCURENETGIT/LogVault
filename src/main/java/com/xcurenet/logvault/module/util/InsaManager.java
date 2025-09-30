package com.xcurenet.logvault.module.util;

import com.xcurenet.common.types.EMail;
import com.xcurenet.common.types.IP;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.loader.UserInsaInfoData;
import com.xcurenet.logvault.loader.type.UserInfo;
import com.xcurenet.logvault.module.ScanData;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class InsaManager {
	protected final Config conf;
	protected final UserInsaInfoData userInsaInfo;

	/**
	 * 사용자 정보 탐색
	 */
	public UserInfo getUser(final ScanData msg) {
		return getUserInfoByIp(msg.getMsgData().getSourceIp());
	}

	protected UserInfo getUserInfoByIp(final IP ip) {
		return userInsaInfo.getUserByIP(ip);
	}

	public String toString(final EMail mail) {
		if (mail == null) return null;
		return mail.getEmailAddr();
	}
}
