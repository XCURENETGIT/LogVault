package com.xcurenet.logvault.privacy.validator.service.brn;

import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class DigitValidator implements DefaultCheck {

	@Override
	public ValidationResult validate(final String input, final String context) {
		if (input == null) {
			return ValidationResult.fail("Business registration number is null");
		}

		String digits = input.replaceAll("[^0-9]", "");
		if (!digits.matches("\\d{10}")) {
			return ValidationResult.fail("Business number must be 10 digits: " + input);
		}

		if (digits.chars().distinct().count() == 1) {
			return ValidationResult.fail("Business number cannot be all identical digits: " + input);
		}

		if (digits.equals("0000000000")) {
			return ValidationResult.fail("Business number cannot be all zeros");
		}
		if (digits.charAt(0) == '0') {
			return ValidationResult.fail("Business number cannot start with 0: " + input);
		}

		int[] multipliers = {1, 3, 7, 1, 3, 7, 1, 3, 5};
		int sum = 0;
		for (int i = 0; i < multipliers.length; i++) {
			int digit = digits.charAt(i) - '0';
			int product = digit * multipliers[i];
			if (i == 8) sum += (product / 10) + (product % 10);
			else sum += product;
		}

		int checkDigit = (10 - (sum % 10)) % 10;
		if (checkDigit != (digits.charAt(9) - '0')) {
			return ValidationResult.fail("Invalid business registration checksum: " + input);
		}
		return ValidationResult.ok();
	}
}
