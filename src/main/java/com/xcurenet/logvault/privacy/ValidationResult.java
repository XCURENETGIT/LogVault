package com.xcurenet.logvault.privacy;

public record ValidationResult(boolean valid, String message) {

	public static ValidationResult ok() {
		return new ValidationResult(true, "VALID");
	}

	public static ValidationResult fail(String msg) {
		return new ValidationResult(false, msg);
	}
}
