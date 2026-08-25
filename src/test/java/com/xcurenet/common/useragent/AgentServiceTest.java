package com.xcurenet.common.useragent;

import com.xcurenet.common.utils.HttpHeaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua_parser.Client;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("User-Agent parser fallback")
class AgentServiceTest {

	private static final String ANTIGRAVITY =
			"antigravity/ide/2.5.5 (aidev_client; os_type=windows; arch=amd64)";
	private static final String CHROME =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
					+ "(KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36";
	private static final String CHROME_WITH_CUSTOM_OS =
			"Mozilla/5.0 Chrome/151.0.0.0 (os_type=linux; arch=amd64)";

	private AgentService agentService;

	@BeforeEach
	void setUp() {
		agentService = new AgentService();
	}

	@Test
	@DisplayName("표준 파서가 Other를 반환하는 app UA를 fallback regex로 파싱")
	void parseAppUserAgent() {
		Client client = agentService.parse(ANTIGRAVITY);

		assertNotNull(client);
		assertEquals("antigravity/ide", client.userAgent.family);
		assertEquals("2.5.5", client.userAgent.major);
		assertNull(client.userAgent.minor);
		assertEquals("Windows", client.os.family);
		assertEquals("Other", client.device.family);
	}

	@Test
	@DisplayName("기존 브라우저 UA는 표준 파서 결과를 그대로 유지")
	void preserveStandardBrowserParsing() {
		Client client = agentService.parse(CHROME);

		assertEquals("Chrome", client.userAgent.family);
		assertEquals("151", client.userAgent.major);
		assertEquals("0", client.userAgent.minor);
		assertEquals("Windows", client.os.family);
		assertEquals("10", client.os.major);
	}

	@Test
	@DisplayName("표준 UA가 인식되어도 누락된 OS 정보는 fallback으로 보완")
	void enrichMissingOsForRecognizedUserAgent() {
		Client client = agentService.parse(CHROME_WITH_CUSTOM_OS);

		assertEquals("Chrome", client.userAgent.family);
		assertEquals("151", client.userAgent.major);
		assertEquals("Linux", client.os.family);
	}

	@Test
	@DisplayName("실제 HTTP 헤더 파싱 경로도 공통 AgentService를 사용")
	void httpHeaderUtilUsesFallbackParser() {
		Client client = HttpHeaderUtil.parse(ANTIGRAVITY);

		assertEquals("antigravity/ide", client.userAgent.family);
		assertEquals("2.5.5", client.userAgent.major);
		assertEquals("Windows", client.os.family);
	}

	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("diverseUserAgents")
	@DisplayName("다양한 app/CLI User-Agent 형식을 예외 없이 파싱")
	void parseDiverseUserAgents(String raw, String expectedFamily, String expectedVersion, String expectedOs) {
		Client client = assertDoesNotThrow(() -> agentService.parse(raw));

		assertNotNull(client);
		assertNotNull(client.userAgent);
		assertNotNull(client.os);
		assertNotNull(client.device);
		assertEquals(expectedFamily, client.userAgent.family);
		assertEquals(expectedVersion, client.userAgent.major);
		assertEquals(expectedOs, client.os.family);
	}

	private static Stream<Arguments> diverseUserAgents() {
		return Stream.of(
				Arguments.of(ANTIGRAVITY, "antigravity/ide", "2.5.5", "Windows"),
				Arguments.of("GithubCopilot/1.250.0", "GithubCopilot", "1.250.0", "Other"),
				Arguments.of("python-requests/2.31.0", "Python Requests", "2", "Other"),
				Arguments.of("curl/8.7.1", "curl", "8", "Other"),
				Arguments.of("my-agent/1.2.3-beta.7 (os_type=linux; arch=amd64)",
						"my-agent", "1.2.3-beta.7", "Linux"),
				Arguments.of("custom-agent v1.2.3 (os_type=macos; arch=arm64)",
						"custom-agent", "1.2.3", "macOS"),
				Arguments.of("custom-agent 1.0 (os_type=win64; arch=amd64)",
						"custom-agent", "1.0", "Windows"),
				Arguments.of("tool 2026.08.18 (os_type=linux)", "tool", "2026.08.18", "Linux"),
				Arguments.of("no-version-agent (os_type=linux; arch=amd64)", "Other", null, "Linux"),
				Arguments.of("custom-agent/1.0 (os_type=android; arch=arm64)",
						"custom-agent", "1.0", "android"),
				Arguments.of("custom-agent/1.0 (os_type=ios; arch=arm64)",
						"custom-agent", "1.0", "ios")
		);
	}

	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("standardBrowserUserAgents")
	@DisplayName("주요 브라우저 User-Agent는 표준 파서 결과를 유지")
	void preserveVariousStandardBrowsers(String raw) {
		Client client = assertDoesNotThrow(() -> agentService.parse(raw));

		assertNotNull(client);
		assertNotNull(client.userAgent);
		assertNotEquals("Other", client.userAgent.family);
	}

	private static Stream<String> standardBrowserUserAgents() {
		return Stream.of(
				CHROME,
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) "
						+ "Gecko/20100101 Firefox/142.0",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
						+ "(KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0",
				"Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 "
						+ "(KHTML, like Gecko) Version/17.5 Safari/605.1.15"
		);
	}

	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("malformedOrMinimalUserAgents")
	@DisplayName("비어 있거나 깨진 User-Agent도 예외 없이 처리")
	void parseMalformedOrMinimalUserAgentsWithoutException(String raw) {
		assertDoesNotThrow(() -> agentService.parse(raw));
	}

	private static Stream<String> malformedOrMinimalUserAgents() {
		return Stream.of(
				null,
				"",
				" ",
				"???",
				"agent/",
				"agent (os_type=windows"
		);
	}

	@Test
	@DisplayName("버전이 없는 비정형 UA도 예외 없이 OS 힌트를 파싱")
	void parseUnversionedUserAgentWithoutException() {
		Client client = assertDoesNotThrow(() -> agentService.parse("custom-agent (os_type=linux; arch=amd64)"));

		assertNotNull(client);
		assertEquals("Other", client.userAgent.family);
		assertEquals("Linux", client.os.family);
	}

	@Test
	@DisplayName("null UA도 예외 없이 처리")
	void parseNullWithoutException() {
		assertDoesNotThrow(() -> agentService.parse(null));
	}
}
