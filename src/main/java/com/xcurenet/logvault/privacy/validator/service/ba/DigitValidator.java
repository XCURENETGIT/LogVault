package com.xcurenet.logvault.privacy.validator.service.ba;

import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class DigitValidator implements DefaultCheck {
	private static final int MIN_DIGITS = 10;
	private static final int MAX_DIGITS = 14;

	@Override
	public ValidationResult validate(final String input, final String context) {
		if (input == null || input.isBlank()) return ValidationResult.fail("INPUT_IS_NULL_OR_EMPTY");

		String digits = extractDigits(input);
		if (digits.isEmpty()) {
			return ValidationResult.fail("NO_DIGITS_FOUND");
		}
		int count = digits.length();
		if (count < MIN_DIGITS) {
			return ValidationResult.fail("DIGIT_LENGTH_TOO_SHORT MIN=" + MIN_DIGITS + " ACTUAL=" + count);
		}

		if (count > MAX_DIGITS) {
			return ValidationResult.fail("DIGIT_LENGTH_TOO_LONG MAX=" + MAX_DIGITS + " ACTUAL=" + count);
		}
		return ValidationResult.ok();
	}

	/**
	 * 문자열에서 숫자만 추출
	 */
	public static String extractDigits(String input) {
		if (input == null || input.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < input.length(); i++) {
			char ch = input.charAt(i);
			if (Character.isDigit(ch)) {
				sb.append(ch);
			}
		}
		return sb.toString();
	}
}
