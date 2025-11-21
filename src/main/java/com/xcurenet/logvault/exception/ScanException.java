package com.xcurenet.logvault.exception;

import com.xcurenet.common.error.ErrorCode;

import java.io.Serial;

public class ScanException extends LogVaultException {

	@Serial
	private static final long serialVersionUID = -5714776608145039680L;

	public ScanException(ErrorCode code) {
		super(code);
	}

	public ScanException(ErrorCode code, Throwable cause) {
		super(code, cause);
	}

	public ScanException(ErrorCode code, String message, Throwable cause) {
		super(code, message, cause);
	}

	public ScanException with(String key, Object val) {
		super.add(key, val);
		return this;
	}

	public ScanException log() {
		super.print();
		return this;
	}
}
