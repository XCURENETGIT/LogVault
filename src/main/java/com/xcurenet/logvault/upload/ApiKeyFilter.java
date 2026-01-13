package com.xcurenet.logvault.upload;

import com.xcurenet.common.utils.Common;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Log4j2
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

	@Value("${upload.basic.username}")
	private String username;

	@Value("${upload.basic.password}")
	private String password;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/upload");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest req, @NonNull HttpServletResponse res, @NonNull FilterChain chain) throws IOException, ServletException {
		String auth = req.getHeader("Authorization");
		if (auth == null || !auth.startsWith("Basic ")) {
			log.warn("[UPLOAD_AUTH_FAIL] IP:{} REASON:no_auth_header", Common.getClientIp(req));
			reject(res);
			return;
		}

		try {
			String base64 = auth.substring(6);
			String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
			String[] parts = decoded.split(":", 2);
			if (parts.length != 2) {
				reject(res);
				return;
			}

			if (!username.equals(parts[0]) || !password.equals(parts[1])) {
				log.warn("[UPLOAD_AUTH_FAIL] IP:{} USER:{}", Common.getClientIp(req), parts[0]);
				reject(res);
				return;
			}
		} catch (Exception e) {
			reject(res);
			return;
		}
		chain.doFilter(req, res);
	}

	private void reject(HttpServletResponse res) throws IOException {
		res.setHeader("WWW-Authenticate", "Basic realm=\"upload\"");
		res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
	}
}