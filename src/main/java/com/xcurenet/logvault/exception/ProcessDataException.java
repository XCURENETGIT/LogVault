package com.xcurenet.logvault.exception;

import com.xcurenet.common.error.ErrorCode;
import lombok.extern.log4j.Log4j2;

import java.io.Serial;

@Log4j2
public class ProcessDataException extends LogVaultException {
	@Serial
	private static final long serialVersionUID = -5714776608145039680L;

	public ProcessDataException(ErrorCode code) {
		super(code);
	}

	public ProcessDataException(ErrorCode code, Throwable cause) {
		super(code, cause);
	}

	public ProcessDataException(ErrorCode code, String message, Throwable cause) {
		super(code, message, cause);
	}

	public ProcessDataException with(String key, Object val) {
		super.add(key, val);
		return this;
	}

	public ProcessDataException log() {
		super.print();
		return this;
	}
}
