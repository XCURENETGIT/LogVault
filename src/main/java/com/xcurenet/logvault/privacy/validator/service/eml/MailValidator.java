package com.xcurenet.logvault.privacy.validator.service.eml;

import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class MailValidator implements DefaultCheck {
	@Override
	public ValidationResult validate(final String input, final String context) {
		if (input == null || input.isBlank()) return ValidationResult.fail("INPUT_IS_NULL_OR_EMPTY");

		int len = input.length();
		if (len < 5) {
			return ValidationResult.fail("INPUT_TOO_SHORT");
		}

		int atIndex = -1;
		int dotIndex = -1;
		int atCount = 0;
		for (int i = 0; i < len; i++) {
			char c = input.charAt(i);
			if (c == '@') {
				atIndex = i;
				atCount++;
			} else if (c == '.' && atIndex != -1) {
				dotIndex = i;
			}
		}
		if (atCount != 1) { // Must have exactly one '@'
			return ValidationResult.fail("INVALID_AT_COUNT");
		}
		if (atIndex == 0) { // Must have local part
			return ValidationResult.fail("LOCAL_PART_MISSING");
		}

		if (dotIndex == -1) { // Must have domain with at least one dot
			return ValidationResult.fail("DOMAIN_DOT_MISSING");
		}

		if (dotIndex <= atIndex + 1) { // Dot must be after '@' and not immediately after
			return ValidationResult.fail("INVALID_DOT_POSITION");
		}

		if (dotIndex == len - 1) { // Dot must not be the last character
			return ValidationResult.fail("DOT_AT_END");
		}
		return ValidationResult.ok();
	}
}
