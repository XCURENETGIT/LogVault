package com.xcurenet.logvault.privacy.validator.service.mnc;


import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class DigitValidator implements DefaultCheck {
	@Override
	public ValidationResult validate(final String input, final String context) {
		if (input == null) return ValidationResult.fail("MAC address is null or empty");

		String normalized = input.toLowerCase().replace("-", ":");
		if (normalized.equals("00:00:00:00:00:00")) {
			return ValidationResult.fail("Invalid MAC: all zeros");
		}
		if (normalized.equals("ff:ff:ff:ff:ff:ff")) {
			return ValidationResult.fail("Invalid MAC: broadcast address");
		}

		String[] parts = normalized.split(":");
		boolean allSame = true;
		for (int i = 1; i < parts.length; i++) {
			if (!parts[i].equals(parts[0])) {
				allSame = false;
				break;
			}
		}
		if (allSame) {
			return ValidationResult.fail("Invalid MAC: repetitive octets " + input);
		}

		String oui = String.join(":", parts[0], parts[1], parts[2]);
		if (oui.equals("00:00:00") || oui.equals("ff:ff:ff")) {
			return ValidationResult.fail("Invalid OUI (vendor code): " + input);
		}
		return ValidationResult.ok();
	}
}
