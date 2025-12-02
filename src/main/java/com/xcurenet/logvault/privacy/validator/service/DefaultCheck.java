package com.xcurenet.logvault.privacy.validator.service;


import com.xcurenet.logvault.privacy.ValidationResult;

public interface DefaultCheck {
	ValidationResult validate(final String input, final String context);
}
