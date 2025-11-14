package com.xcurenet.logvault.exception;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Log4j2
public class LogVaultException extends RuntimeException {

	private final ErrorCode errorCode;
	private final Map<String, Object> context = new HashMap<>();

	public LogVaultException(ErrorCode code) {
		this(code, code != null ? code.getMessageTemplate() : null, null);
	}

	public LogVaultException(ErrorCode code, Throwable cause) {
		this(code, code != null ? code.getMessageTemplate() : null, cause);
	}

	public LogVaultException(ErrorCode code, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = code;
	}

	public String codeOrDefault() {
		return errorCode != null ? errorCode.getCode() : ErrorCode.UNKNOWN_ERROR.getCode();
	}

	public void add(String key, Object val) {
		context.put(key, val);
	}

	public void addAll(Map<String, ?> map) {
		if (map != null) map.forEach(this::add);
	}

	public void print() {
		String code = codeOrDefault();
		String msg = Optional.ofNullable(Common.formattedMessage(getMessage(), context)).orElse(getMessage());
		if (getCause() != null) {
			log.error("{} | {} | {} | {}", code, Common.extractOrigin(getCause()), msg, context, getCause());
		} else {
			log.error("{} | {} | {}", code, msg, context);
		}
	}
}
