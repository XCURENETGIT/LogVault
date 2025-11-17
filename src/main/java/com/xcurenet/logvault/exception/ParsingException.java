package com.xcurenet.logvault.exception;

import com.xcurenet.common.error.ErrorCode;

import java.io.Serial;

public class ParsingException extends LogVaultException {

	@Serial
	private static final long serialVersionUID = -5714776608145039680L;

	public ParsingException(ErrorCode code) {
		super(code);
	}

	public ParsingException(ErrorCode code, Throwable cause) {
		super(code, cause);
	}

	public ParsingException(ErrorCode code, String message, Throwable cause) {
		super(code, message, cause);
	}

	public ParsingException with(String key, Object val) {
		super.add(key, val);
		return this;
	}

	public ParsingException log() {
		super.print();
		return this;
	}
}
