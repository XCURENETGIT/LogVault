package com.xcurenet.logvault.module.log;

import com.xcurenet.common.utils.CommonUtil;
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
			String bodySize = msg.getBody() != null ? CommonUtil.convertFileSize(msg.getBody().getSize()) : "0";
			String attSize = CommonUtil.convertFileSize(msg.getAttachTotalSize());

			EmassDoc.User user = msg.getUser();
			String deptNm = user != null ? user.getDeptName() : "";
			String userId = user != null ? user.getId() : "";
			String userName = user != null ? user.getName() : "";

			EmassDoc.Network net = msg.getNetwork();
			String sIp = net != null ? net.getSrcIp() : "";
			String dIp = net != null ? net.getDstIp() : "";
			int sPort = net != null ? net.getSrcPort() : 0;
			int dPort = net != null ? net.getDstPort() : 0;

			EmassDoc.Http http = msg.getHttp();
			String url = http != null ? http.getUrl() : "";
			String agent = getUserAgent(http);
			log.info("[MSG_DONE] {} | {} | BODY:{} ({}) | AT_CNT:{} ({}) | {} | {} | {} | {}:{} > {}:{} | {} | {} | {} | {}\n", msg.getMsgid(), msg.getService().getSvc(), isBody, bodySize, attachCnt, attSize, deptNm, userId, userName, sIp, sPort, dIp, dPort, data.getFileName(), url, agent, DateUtils.duration(data.getStart()));
		} catch (Exception e) {
			log.warn("[DEBUG_LOG] {} | {}", msg.getMsgid(), e.getMessage());
			log.error("", e);
		}
	}

	private String getUserAgent(final EmassDoc.Http http) {
		if (http == null || http.getAgent() == null) return null;
		if (http.getAgent().getClient() == null) return null;
		return http.getAgent().getClient() + " | " + http.getAgent().getClientVersion();
	}
}
