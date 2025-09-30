package com.xcurenet.logvault.exception;

import java.io.Serial;

public class SkipFileException extends Exception {
	@Serial
	private static final long serialVersionUID = -5748372569079732077L;

	public SkipFileException(final String message) {
		super(message);
	}
}
