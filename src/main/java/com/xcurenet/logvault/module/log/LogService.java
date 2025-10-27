package com.xcurenet.logvault.module.log;

import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class LogService {
	public void log(final ScanData data) {
		EmassDoc msg = data.getEmassDoc();
		try {
			boolean isBody = (msg.getBody() != null && msg.getBody().getSize() > 0);
			int attachCnt = msg.getAttachCount();
			int attachExistCnt = msg.getAttachExistCount();
			String bodySize = msg.getBody() != null ? Common.convertFileSize(msg.getBody().getSize()) : "0";
			String bodyLang = msg.getBody() != null ? msg.getBody().getLanguage() : "";
			String attSize = Common.convertFileSize(msg.getAttachTotalSize());

			EmassDoc.User user = msg.getUser();
			String deptName = user.getDeptName() != null ? user.getDeptName() : "unknown dept";
			String userId = user.getId() != null ? user.getId() : "unknown userId";
			String userName = (user.getName() != null ? user.getName() : "unknown userName") + " " + user.getJikgubName();

			EmassDoc.Network net = msg.getNetwork();
			String sIp = net != null ? net.getSrcIp() : "";
			String dIp = net != null ? net.getDstIp() : "";
			int sPort = net != null ? net.getSrcPort() : 0;
			int dPort = net != null ? net.getDstPort() : 0;

			EmassDoc.Http http = msg.getHttp();
			String url = http != null ? http.getUrl() : "";
			String agent = getUserAgent(http);
			log.info("[MSG_DONE] {} | {} | BODY:{} ({}) {} | AT_CNT:{} | EXIST_CNT:{} ({}) | {} | {} | {} | {}:{} > {}:{} | {} | {} | {}\n", msg.getMsgid(), msg.getService().getSvc(), isBody, bodySize, bodyLang, attachCnt, attachExistCnt, attSize, deptName, userId, userName, sIp, sPort, dIp, dPort, url, agent, DateUtils.stop(data.getStopWatch()));
		} catch (Exception e) {
			log.warn("[DEBUG_LOG] {} | {}", msg.getMsgid(), e.getMessage());
			log.error("", e);
		}
	}

	private String getUserAgent(final EmassDoc.Http http) {
		if (http == null || http.getAgent() == null) return null;
		return http.getAgent().getOs() + " " + http.getAgent().getClient();
	}
}
