package com.xcurenet.logvault.exception;

import com.xcurenet.common.error.ErrorCode;

import java.io.Serial;

public class FilterException extends LogVaultException {

	@Serial
	private static final long serialVersionUID = -5714776608145039680L;

	public FilterException(ErrorCode code) {
		super(code);
	}

	public FilterException(ErrorCode code, Throwable cause) {
		super(code, cause);
	}

	public FilterException(ErrorCode code, String message, Throwable cause) {
		super(code, message, cause);
	}

	public FilterException with(String key, Object val) {
		super.add(key, val);
		return this;
	}

	public FilterException log() {
		super.print();
		return this;
	}
}

