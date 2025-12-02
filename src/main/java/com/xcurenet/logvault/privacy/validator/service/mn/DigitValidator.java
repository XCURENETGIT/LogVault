package com.xcurenet.logvault.privacy.validator.service.mn;

import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class DigitValidator implements DefaultCheck {
	@Override
	public ValidationResult validate(final String input, final String context) {
		if (input == null) return ValidationResult.fail("Phone number is null");

		String digits = input.replaceAll("[^0-9]", "");
		if (!(digits.startsWith("010") || digits.startsWith("011") || digits.startsWith("016") || digits.startsWith("017") || digits.startsWith("018") || digits.startsWith("019"))) {
			return ValidationResult.fail("Invalid phone prefix: " + input);
		}
		if (digits.length() < 10 || digits.length() > 11) {
			return ValidationResult.fail("Invalid phone length: " + input);
		}
		return ValidationResult.ok();
	}
}
