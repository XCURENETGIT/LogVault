package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("UserAgentAnalysis - 요청 전용 HTTP 헤더 처리 검증")
class UserAgentAnalysisTest {

	@Test
	@DisplayName("응답 헤더가 없는 확장 플러그인 hdr도 예외 없이 요청 정보와 UA를 분석함")
	void requestOnlyHeader_shouldNotFail() throws Exception {
		Path headerFile = Files.createTempFile("xgenai-request-only-", ".hdr");
		Files.writeString(headerFile, "POST /v1/messages HTTP/1.1\n"
				+ "Host: chatgpt.com\n"
				+ "Origin: https://chatgpt.com\n"
				+ "User-Agent: TestBrowser/1.0\n\n", StandardCharsets.UTF_8);

		try {
			Config config = mock(Config.class);
			when(config.getPath("request-only.hdr", false)).thenReturn(headerFile.toString());
			UserAgentAnalysis analysis = new UserAgentAnalysis(config);

			MSGData msgData = new MSGData();
			msgData.setHeader("request-only.hdr");
			msgData.setAction("ALLOW");
			EmassDoc.Http http = new EmassDoc.Http();
			EmassDoc document = new EmassDoc();
			document.setHttp(http);
			ScanData scanData = mock(ScanData.class);
			when(scanData.getMsgData()).thenReturn(msgData);
			when(scanData.getEmassDoc()).thenReturn(document);

			assertDoesNotThrow(() -> analysis.detect(scanData));
			assertNotNull(http.getHeader());
			assertEquals("POST", http.getHeader().getRequest().getMethod());
			assertEquals("https://chatgpt.com", http.getHeader().getRequest().getOrigin());
			assertNull(http.getHeader().getResponse());
			assertNotNull(http.getAgent());
			assertEquals("TestBrowser", http.getAgent().getClient());
		} finally {
			Files.deleteIfExists(headerFile);
		}
	}
}
