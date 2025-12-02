package com.xcurenet.logvault.privacy.validator;


import com.xcurenet.logvault.privacy.ValidationResult;

public interface PatternValidator {
	ValidationResult validate(final String value, final String context);
}
