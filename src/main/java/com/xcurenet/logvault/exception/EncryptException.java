package com.xcurenet.logvault.exception;

import com.xcurenet.common.error.ErrorCode;

import java.io.Serial;

public class EncryptException extends LogVaultException {

	@Serial
	private static final long serialVersionUID = -5714776608145039680L;

	public EncryptException(ErrorCode code) {
		super(code);
	}

	public EncryptException(ErrorCode code, Throwable cause) {
		super(code, cause);
	}

	public EncryptException(ErrorCode code, String message, Throwable cause) {
		super(code, message, cause);
	}

	public EncryptException with(String key, Object val) {
		super.add(key, val);
		return this;
	}

	public EncryptException log() {
		super.print();
		return this;
	}
}
