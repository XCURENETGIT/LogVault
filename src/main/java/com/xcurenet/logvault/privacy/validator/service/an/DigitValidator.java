package com.xcurenet.logvault.privacy.validator.service.an;

import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

import java.util.regex.Pattern;

public class DigitValidator implements DefaultCheck {
	private static final Pattern KEYWORDS = Pattern.compile("(로|길|동|읍|면|구)");

	@Override
	public ValidationResult validate(final String input, final String context) {
		if (input == null) return ValidationResult.fail("Address is null");

		if (!KEYWORDS.matcher(input).find()) {
			return ValidationResult.fail("Address does not contain valid keywords: " + input);
		}
		return ValidationResult.ok();
	}
}
