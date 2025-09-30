package com.xcurenet.logvault.exception;

import java.io.Serial;

public class FileSendException extends Exception {

	@Serial
	private static final long serialVersionUID = -5714776608145039680L;

	public FileSendException(final Throwable cause) {
		super(cause);
	}
}
