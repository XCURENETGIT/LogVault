package com.xcurenet.logvault.privacy.validator.service.pn;

import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class DigitValidator implements DefaultCheck {
	@Override
	public ValidationResult validate(final String input, final String context) {
		if (input == null || input.length() < 9) {
			return ValidationResult.fail("Passport number is null or too short");
		}

		int letters = 0;
		int digits = 0;
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (Character.isWhitespace(c) || c == '-') continue;

			if (Character.isLetter(c)) {
				if (digits > 0) {
					return ValidationResult.fail("Invalid passport format: letters must precede digits");
				}
				letters++;
			} else if (Character.isDigit(c)) {
				digits++;
			} else {
				return ValidationResult.fail("Invalid passport format: illegal character detected");
			}
		}
		if (letters >= 1 && letters <= 2 && digits >= 7 && digits <= 8) {
			return ValidationResult.ok();
		}
		return ValidationResult.fail("Invalid passport format: must be 1–2 letters followed by 7–8 digits");
	}
}
