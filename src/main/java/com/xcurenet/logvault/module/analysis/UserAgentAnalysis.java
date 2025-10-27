package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.common.utils.FileUtil;
import com.xcurenet.common.utils.HttpHeaderUtil;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ua_parser.Client;

import java.io.File;
import java.time.ZonedDateTime;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserAgentAnalysis {
	private final Config conf;

	public void detect(final ScanData scanData) {
		MSGData msg = scanData.getMsgData();
		if (msg.getHeader() == null || !new File(conf.getPath(msg.getHeader())).exists()) return;

		final String raw = FileUtil.getText(conf.getPath(msg.getHeader()));
		HttpHeaderUtil.HttpHeader httpHeader = HttpHeaderUtil.parserHeader(raw);
		HttpHeaderUtil.HttpHeader.HttpRequestHeader request = httpHeader.getRequestHeader();
		HttpHeaderUtil.HttpHeader.HttpResponseHeader response = httpHeader.getResponseHeader();

		EmassDoc.Header.RequestHeader requestHeader = EmassDoc.Header.RequestHeader.builder().method(request.getMethod()).protocol(request.getProtocol()).origin(request.getHeaders().get("origin")).build();
		EmassDoc.Header.ResponseHeader responseHeader = EmassDoc.Header.ResponseHeader.builder().date(getHeaderDate(response)).contentType(response.getHeaders().get("content-type")).build();
		scanData.getEmassDoc().getHttp().setHeader(EmassDoc.Header.builder().request(requestHeader).response(responseHeader).build());

		Client client = httpHeader.getClient();
		if (client != null) {
			EmassDoc.Agent agent = new EmassDoc.Agent();
			agent.setRaw(httpHeader.getAgentString());
			agent.setDevice(client.device != null ? client.device.family : null);
			agent.setOs(client.os != null ? client.os.family : null);
			agent.setOsVersion(client.os != null ? client.os.major : null);
			agent.setClient(client.userAgent != null ? client.userAgent.family : null);
			agent.setClientVersion(String.join(".", client.userAgent != null ? client.userAgent.major : "0", client.userAgent != null ? client.userAgent.minor : "0"));
			scanData.getEmassDoc().getHttp().setAgent(agent);
		}
	}

	private ZonedDateTime getHeaderDate(final HttpHeaderUtil.HttpHeader.HttpResponseHeader response) {
		String date = response.getHeaders().get("date");
		if (Common.isNotEmpty(date)) {
			return ZonedDateTime.parse(date, DateUtils.RESPONSE_DATETIME);
		}
		return null;
	}
}
