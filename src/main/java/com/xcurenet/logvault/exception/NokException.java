package com.xcurenet.logvault.exception;

import java.io.Serial;

public class NokException extends Exception {
	@Serial
	private static final long serialVersionUID = 3944949145895375765L;

	public NokException(final String message) {
		super(message);
	}
}
