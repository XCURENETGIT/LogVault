package com.xcurenet.privacydetector.validator.service.ssn;


import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class DigitValidator implements DefaultCheck {
	@Override
	public ValidationResult validate(final String input, final String context) {
		if (input == null) return ValidationResult.fail("SSN is null");

		String digits = input.replaceAll("[^0-9]", "");
		if (!digits.matches("\\d{9}")) {
			return ValidationResult.fail("SSN must be 9 digits: " + input);
		}
		String area = digits.substring(0, 3);
		String group = digits.substring(3, 5);
		String serial = digits.substring(5);

		if (area.equals("000") || area.equals("666") || Integer.parseInt(area) >= 900) {
			return ValidationResult.fail("Invalid SSN area: " + input);
		}
		if (group.equals("00")) return ValidationResult.fail("Invalid SSN group: " + input);
		if (serial.equals("0000")) return ValidationResult.fail("Invalid SSN serial: " + input);
		return ValidationResult.ok();
	}
}
