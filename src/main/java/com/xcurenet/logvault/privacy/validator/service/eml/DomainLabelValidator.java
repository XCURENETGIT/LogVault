package com.xcurenet.logvault.privacy.validator.service.eml;

import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class DomainLabelValidator implements DefaultCheck {

	@Override
	public ValidationResult validate(String input, String context) {
		if (input == null || input.isBlank()) return ValidationResult.fail("INPUT_IS_NULL_OR_EMPTY");

		int at = input.indexOf('@');
		if (at < 0) return ValidationResult.fail("NO_AT");

		String domain = input.substring(at + 1);
		String[] labels = domain.split("\\.");
		for (String label : labels) {
			if (label.isEmpty()) {
				return ValidationResult.fail("EMPTY_LABEL");
			}
			if (label.startsWith("-") || label.endsWith("-")) {
				return ValidationResult.fail("INVALID_HYPHEN_POSITION");
			}
		}
		return ValidationResult.ok();
	}
}
