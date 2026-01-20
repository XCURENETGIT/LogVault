package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.FileUtil;
import com.xcurenet.common.utils.HttpHeaderUtil;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ua_parser.Client;

import java.nio.file.Files;
import java.nio.file.Paths;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserAgentAnalysis {

	private final Config conf;

	public void detect(final ScanData scanData) {
		if (scanData.getMsgData() == null) {
			log.warn("{}", ErrorCode.UA_MSGDATA_NULL.toString());
			return;
		}

		MSGData msg = scanData.getMsgData();
		if (msg.getHeader() == null) {
			log.debug("{}", ErrorCode.UA_HEADER_PATH_NULL.toString());
			return;
		}

		String headerPath = conf.getPath(msg.getHeader());
		if (!Files.exists(Paths.get(headerPath))) {
			log.warn("{} | PATH:{}", ErrorCode.UA_HEADER_FILE_NOT_FOUND.toString(), headerPath);
			return;
		}

		try {
			final String raw = FileUtil.getText(headerPath);
			HttpHeaderUtil.HttpHeader httpHeader;
			try {
				httpHeader = HttpHeaderUtil.parserHeader(raw);
			} catch (Exception e) {
				log.warn("{} | PATH:{} ERR:{}", ErrorCode.UA_HEADER_PARSE_FAIL.toString(), headerPath, e.toString());
				return;
			}
			if (scanData.getEmassDoc() == null || scanData.getEmassDoc().getHttp() == null) {
				log.warn("{}", ErrorCode.UA_EMASSDOC_HTTP_NULL.toString());
				return;
			}

			HttpHeaderUtil.HttpHeader.HttpRequestHeader request = httpHeader.getRequestHeader();
			HttpHeaderUtil.HttpHeader.HttpResponseHeader response = httpHeader.getResponseHeader();

			EmassDoc.Header.RequestHeader requestHeader = EmassDoc.Header.RequestHeader.builder().method(request.getMethod()).protocol(request.getProtocol()).origin(getOrigin(request)).build();
			EmassDoc.Header.ResponseHeader responseHeader = EmassDoc.Header.ResponseHeader.builder().date(getHeaderDate(response)).contentType(getContentType(response)).build();
			scanData.getEmassDoc().getHttp().setHeader(EmassDoc.Header.builder().request(requestHeader).response(responseHeader).build());
			try {
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
			} catch (Exception e) {
				log.warn("{} | ERR:{}", ErrorCode.UA_AGENT_PARSE_FAIL.toString(), e.toString());
			}
			scanData.getEmassDoc().setTestMessage(isTestMessage(raw));
		} catch (Exception e) {
			log.warn("{} | ERR:{}", ErrorCode.UA_ANALYSIS_FAIL.toString(), e.toString());
		}
	}

	private boolean isTestMessage(final String raw) {
		if (raw == null) return false;
		return raw.contains("XCURENET");
	}


	private String getOrigin(final HttpHeaderUtil.HttpHeader.HttpRequestHeader request) {
		if (request.getHeaders() == null) return null;
		return request.getHeaders().get("origin");
	}

	private String getHeaderDate(final HttpHeaderUtil.HttpHeader.HttpResponseHeader response) {
		if (response.getHeaders() == null) return null;
		return response.getHeaders().get("date");
	}

	private String getContentType(final HttpHeaderUtil.HttpHeader.HttpResponseHeader response) {
		if (response.getHeaders() == null) return null;
		return response.getHeaders().get("content-type");
	}
}
