package com.xcurenet.logvault.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileUploadController - Path Traversal 보안 검증")
class FileUploadControllerPathTest {

	private final FileUploadController controller = new FileUploadController();

	/**
	 * resolveSafeFullPath는 private이므로 리플렉션으로 테스트.
	 * 실제 프로젝트에서는 package-private으로 변경하거나 별도 유틸로 분리 권장.
	 */
	private Path callResolveSafeFullPath(String path) throws Exception {
		Method method = FileUploadController.class.getDeclaredMethod("resolveSafeFullPath", jakarta.servlet.http.HttpServletRequest.class, String.class);
		method.setAccessible(true);
		return (Path) method.invoke(controller, new MockHttpServletRequest(), path);
	}

	@Nested
	@DisplayName("정상 케이스")
	class ValidPaths {
		@Test
		@DisplayName("상대 경로 → BASE_DIR 기준 해석")
		void relativePath() throws Exception {
			Path result = callResolveSafeFullPath("msg/info/test.msg");
			assertTrue(result.toString().replace('\\', '/').contains("/users/las/msg/info/test.msg"));
		}

		@Test
		@DisplayName("절대 경로 (BASE_DIR 하위)")
		void absolutePathUnderBase() throws Exception {
			Path result = callResolveSafeFullPath("/users/las/msg/test.msg");
			assertNotNull(result);
		}
	}

	@Nested
	@DisplayName("Path Traversal 차단")
	class PathTraversal {
		@Test
		@DisplayName(".. 포함 → IllegalArgumentException")
		void dotDot_shouldReject() {
			assertThrows(IllegalArgumentException.class, () -> callResolveSafeFullPath("../../../etc/passwd"));
		}

		@Test
		@DisplayName("빈 경로 → IllegalArgumentException")
		void emptyPath_shouldReject() {
			assertThrows(IllegalArgumentException.class, () -> callResolveSafeFullPath(""));
		}

		@Test
		@DisplayName("null 경로 → IllegalArgumentException")
		void nullPath_shouldReject() {
			assertThrows(IllegalArgumentException.class, () -> callResolveSafeFullPath(null));
		}

		@Test
		@DisplayName("BASE_DIR 외부 절대경로 → IllegalArgumentException")
		void outsideBaseDir_shouldReject() {
			assertThrows(IllegalArgumentException.class, () -> callResolveSafeFullPath("/etc/shadow"));
		}

		@Test
		@DisplayName("백슬래시 → 슬래시로 변환 후 검증")
		void backslash_shouldNormalize() {
			assertThrows(IllegalArgumentException.class, () -> callResolveSafeFullPath("..\\..\\etc\\passwd"));
		}
	}

	/**
	 * 최소한의 Mock HttpServletRequest
	 */
	private static class MockHttpServletRequest implements jakarta.servlet.http.HttpServletRequest {
		public String getRemoteAddr() {
			return "127.0.0.1";
		}

		public String getHeader(String name) {
			return null;
		}

		// 나머지 메서드는 테스트에 불필요하므로 기본 구현
		public Object getAttribute(String name) {
			return null;
		}

		public java.util.Enumeration<String> getAttributeNames() {
			return java.util.Collections.emptyEnumeration();
		}

		public String getCharacterEncoding() {
			return null;
		}

		public void setCharacterEncoding(String env) {
		}

		public int getContentLength() {
			return 0;
		}

		public long getContentLengthLong() {
			return 0;
		}

		public String getContentType() {
			return null;
		}

		public jakarta.servlet.ServletInputStream getInputStream() {
			return null;
		}

		public String getParameter(String name) {
			return null;
		}

		public java.util.Enumeration<String> getParameterNames() {
			return java.util.Collections.emptyEnumeration();
		}

		public String[] getParameterValues(String name) {
			return null;
		}

		public java.util.Map<String, String[]> getParameterMap() {
			return java.util.Collections.emptyMap();
		}

		public String getProtocol() {
			return null;
		}

		public String getScheme() {
			return null;
		}

		public String getServerName() {
			return null;
		}

		public int getServerPort() {
			return 0;
		}

		public java.io.BufferedReader getReader() {
			return null;
		}

		public String getRemoteHost() {
			return null;
		}

		public void setAttribute(String name, Object o) {
		}

		public void removeAttribute(String name) {
		}

		public java.util.Locale getLocale() {
			return null;
		}

		public java.util.Enumeration<java.util.Locale> getLocales() {
			return null;
		}

		public boolean isSecure() {
			return false;
		}

		public jakarta.servlet.RequestDispatcher getRequestDispatcher(String path) {
			return null;
		}

		public int getRemotePort() {
			return 0;
		}

		public String getLocalName() {
			return null;
		}

		public String getLocalAddr() {
			return null;
		}

		public int getLocalPort() {
			return 0;
		}

		public jakarta.servlet.ServletContext getServletContext() {
			return null;
		}

		public jakarta.servlet.AsyncContext startAsync() {
			return null;
		}

		public jakarta.servlet.AsyncContext startAsync(jakarta.servlet.ServletRequest a, jakarta.servlet.ServletResponse b) {
			return null;
		}

		public boolean isAsyncStarted() {
			return false;
		}

		public boolean isAsyncSupported() {
			return false;
		}

		public jakarta.servlet.AsyncContext getAsyncContext() {
			return null;
		}

		public jakarta.servlet.DispatcherType getDispatcherType() {
			return null;
		}

		public String getRequestId() {
			return null;
		}

		public String getProtocolRequestId() {
			return null;
		}

		public jakarta.servlet.ServletConnection getServletConnection() {
			return null;
		}

		public String getAuthType() {
			return null;
		}

		public jakarta.servlet.http.Cookie[] getCookies() {
			return null;
		}

		public long getDateHeader(String name) {
			return 0;
		}

		public java.util.Enumeration<String> getHeaders(String name) {
			return java.util.Collections.emptyEnumeration();
		}

		public java.util.Enumeration<String> getHeaderNames() {
			return java.util.Collections.emptyEnumeration();
		}

		public int getIntHeader(String name) {
			return 0;
		}

		public String getMethod() {
			return "POST";
		}

		public String getPathInfo() {
			return null;
		}

		public String getPathTranslated() {
			return null;
		}

		public String getContextPath() {
			return "";
		}

		public String getQueryString() {
			return null;
		}

		public String getRemoteUser() {
			return null;
		}

		public boolean isUserInRole(String role) {
			return false;
		}

		public java.security.Principal getUserPrincipal() {
			return null;
		}

		public String getRequestedSessionId() {
			return null;
		}

		public String getRequestURI() {
			return "/api/upload";
		}

		public StringBuffer getRequestURL() {
			return new StringBuffer("http://localhost/api/upload");
		}

		public String getServletPath() {
			return "";
		}

		public jakarta.servlet.http.HttpSession getSession(boolean create) {
			return null;
		}

		public jakarta.servlet.http.HttpSession getSession() {
			return null;
		}

		public String changeSessionId() {
			return null;
		}

		public boolean isRequestedSessionIdValid() {
			return false;
		}

		public boolean isRequestedSessionIdFromCookie() {
			return false;
		}

		public boolean isRequestedSessionIdFromURL() {
			return false;
		}

		public boolean authenticate(jakarta.servlet.http.HttpServletResponse r) {
			return false;
		}

		public void login(String u, String p) {
		}

		public void logout() {
		}

		public java.util.Collection<jakarta.servlet.http.Part> getParts() {
			return null;
		}

		public jakarta.servlet.http.Part getPart(String name) {
			return null;
		}

		public <T extends jakarta.servlet.http.HttpUpgradeHandler> T upgrade(Class<T> c) {
			return null;
		}
	}
}
