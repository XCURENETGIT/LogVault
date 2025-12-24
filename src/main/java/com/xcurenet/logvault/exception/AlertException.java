package com.xcurenet.logvault.exception;

import com.xcurenet.common.error.ErrorCode;

import java.io.Serial;

public class AlertException extends LogVaultException {

	@Serial
	private static final long serialVersionUID = -5714776608145039680L;

	public AlertException(ErrorCode code) {
		super(code);
	}

	public AlertException(ErrorCode code, Throwable cause) {
		super(code, cause);
	}

	public AlertException(ErrorCode code, String message, Throwable cause) {
		super(code, message, cause);
	}

	public AlertException with(String key, Object val) {
		super.add(key, val);
		return this;
	}

	public AlertException log() {
		super.print();
		return this;
	}
}
