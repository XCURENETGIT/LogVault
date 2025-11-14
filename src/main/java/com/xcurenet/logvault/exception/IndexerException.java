package com.xcurenet.logvault.exception;

import com.xcurenet.common.error.ErrorCode;
import lombok.extern.log4j.Log4j2;

import java.io.Serial;
import java.util.Map;

@Log4j2
public class IndexerException extends LogVaultException {
	@Serial
	private static final long serialVersionUID = -5714776608145039680L;

	public IndexerException(ErrorCode code) {
		super(code);
	}

	public IndexerException(ErrorCode code, Throwable cause) {
		super(code, cause);
	}

	public IndexerException(ErrorCode code, String message, Throwable cause) {
		super(code, message, cause);
	}

	public IndexerException with(String key, Object val) {
		super.add(key, val);
		return this;
	}

	public IndexerException withAll(Map<String, ?> map) {
		super.addAll(map);
		return this;
	}

	public IndexerException log() {
		super.print();
		return this;
	}
}
