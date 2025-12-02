package com.xcurenet.logvault.privacy.validator.service.crn;


import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class DigitValidator implements DefaultCheck {

	@Override
	public ValidationResult validate(final String input, final String context) {
		if (input == null) return ValidationResult.fail("Car registration number is null");

		if (!input.matches(".*[0-9]{2,3}[가-힣][0-9]{4}.*")) {
			return ValidationResult.fail("Invalid car registration format: " + input);
		}
		return ValidationResult.ok();
	}
}
