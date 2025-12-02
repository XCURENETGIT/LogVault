package com.xcurenet.logvault.privacy.validator.service.pn;

import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class DigitValidator implements DefaultCheck {
	@Override
	public ValidationResult validate(final String input, final String context) {
		if (input == null) return ValidationResult.fail("Passport number is null");

		if (!input.matches("^[MSRGD][0-9A-Za-z]{8,9}$")) {
			return ValidationResult.fail("Invalid passport format: " + input);
		}
		return ValidationResult.ok();
	}
}
