package com.xcurenet.logvault.privacy.validator.service.cpn;


import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class CheckSumValidator implements DefaultCheck {
	@Override
	public ValidationResult validate(final String input, final String context) {
		String value = input.replaceAll("[^0-9]", "");
		if (value.length() != 13) return ValidationResult.fail("Invalid for corporate registration number : " + input);

		float sum = 0;
		for (int i = 0; i < 12; i++)
			sum += ((i % 2) + 1) * Float.parseFloat(String.valueOf(value.charAt(i)));

		if (Float.parseFloat(value.substring(12, 13)) != (10 - (sum % 10)) % 10)
			return ValidationResult.fail("Invalid checksum for corporate registration number : " + input);
		return ValidationResult.ok();
	}
}
