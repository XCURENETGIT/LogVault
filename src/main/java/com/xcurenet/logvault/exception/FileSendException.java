package com.xcurenet.logvault.exception;

import com.xcurenet.common.error.ErrorCode;

import java.io.Serial;

public class FileSendException extends LogVaultException {

	@Serial
	private static final long serialVersionUID = -5714776608145039680L;


	public FileSendException(ErrorCode code) {
		super(code);
	}

	public FileSendException(ErrorCode code, Throwable cause) {
		super(code, cause);
	}

	public FileSendException(ErrorCode code, String message, Throwable cause) {
		super(code, message, cause);
	}

	public FileSendException with(String key, Object val) {
		super.add(key, val);
		return this;
	}

	public FileSendException log() {
		super.print();
		return this;
	}
}
