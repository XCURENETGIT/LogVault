package com.xcurenet.logvault.exception;

import com.xcurenet.common.error.ErrorCode;

import java.io.Serial;

public class InsaMappingException extends LogVaultException {

	@Serial
	private static final long serialVersionUID = -5714776608145039680L;

	public InsaMappingException(ErrorCode code) {
		super(code);
	}

	public InsaMappingException(ErrorCode code, Throwable cause) {
		super(code, cause);
	}

	public InsaMappingException(ErrorCode code, String message, Throwable cause) {
		super(code, message, cause);
	}

	public InsaMappingException with(String key, Object val) {
		super.add(key, val);
		return this;
	}

	public InsaMappingException log() {
		super.print();
		return this;
	}
}
