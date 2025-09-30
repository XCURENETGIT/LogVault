package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.FileUtil;
import com.xcurenet.common.utils.HttpHeaderUtil;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ua_parser.Client;

import java.io.File;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserAgentAnalysis {

	public void detect(final ScanData scanData) {
		MSGData msg = scanData.getMsgData();
		if (msg.getHeaderPath() == null || !new File(msg.getHeaderPath()).exists()) return;

		final String raw = FileUtil.getText(msg.getHeaderPath());
		HttpHeaderUtil.HttpHeader header = HttpHeaderUtil.parserHeader(raw);
		Client client = header.getClient();
		if (client != null) {
			EmassDoc.Agent agent = new EmassDoc.Agent();
			agent.setRaw(raw);
			agent.setDevice(client.device.family);
			agent.setOs(client.os.family);
			agent.setOsVersion(client.os.major);
			agent.setClient(client.userAgent.family);
			agent.setClientVersion(String.join(".", client.userAgent.major != null ? client.userAgent.major : "0", client.userAgent.minor != null ? client.userAgent.minor : "0", client.userAgent.patch != null ? client.userAgent.patch : "0"));
			scanData.getEmassDoc().getHttp().setAgent(agent);
		}
	}
}
