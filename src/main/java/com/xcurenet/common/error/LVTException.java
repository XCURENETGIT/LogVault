package com.xcurenet.common.error;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class LVTException extends RuntimeException {
	private final ErrorCode errorCode;
	private final Map<String, Object> context = new HashMap<>();

	public LVTException(ErrorCode errorCode) {
		super(errorCode.getMessageTemplate());
		this.errorCode = errorCode;
	}

	public LVTException(ErrorCode errorCode, Throwable cause) {
		super(errorCode.getMessageTemplate(), cause);
		this.errorCode = errorCode;
	}

	public LVTException with(String key, Object val) {
		context.put(key, val);
		return this;
	}

	public String code() {
		return errorCode.getCode();
	}

	public String formattedMessage() {
		String msg = errorCode.getMessageTemplate();
		for (Map.Entry<String, Object> e : context.entrySet()) {
			msg = msg.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
		}
		return msg;
	}
}
