package com.xcurenet.logvault.privacy.validator.service.dn;


import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class DigitValidator implements DefaultCheck {
	@Override
	public ValidationResult validate(final String input, final String context) {
		if (input == null || input.isBlank()) {
			return ValidationResult.fail("Driver license number is null or empty");
		}
		// 숫자 자리수 확인
		String digits = input.replaceAll("[^0-9]", "");
		if (digits.length() < 8 || digits.length() > 12) {
			return ValidationResult.fail("Driver license number invalid length: " + input);
		}
		return ValidationResult.ok();
	}
}
