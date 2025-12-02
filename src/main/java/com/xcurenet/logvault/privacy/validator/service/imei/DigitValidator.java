package com.xcurenet.logvault.privacy.validator.service.imei;

import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class DigitValidator implements DefaultCheck {

	@Override
	public ValidationResult validate(final String input, final String context) {
		if (input == null) return ValidationResult.fail("IMEI is null");

		String digits = input.replaceAll("[^0-9]", "");
		if (!digits.matches("\\d{15}")) {
			return ValidationResult.fail("IMEI must be 15 digits: " + input);
		}

		int[] ints = new int[digits.length()];
		for (int i = 0; i < digits.length(); i++) {
			ints[i] = Integer.parseInt(digits.substring(i, i + 1));
		}
		for (int i = ints.length - 2; i >= 0; i = i - 2) {
			int j = ints[i];
			j = j * 2;
			if (j > 9) j = j % 10 + 1;
			ints[i] = j;
		}
		int sum = 0;
		for (int num : ints) {
			sum += num;
		}
		return sum % 10 == 0 ? ValidationResult.ok() : ValidationResult.fail("IMEI checksum for corporate registration number : " + input);
	}
}
