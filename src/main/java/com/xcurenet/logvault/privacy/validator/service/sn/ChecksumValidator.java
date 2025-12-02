package com.xcurenet.logvault.privacy.validator.service.sn;

import com.xcurenet.logvault.privacy.ResidentRegistrationNumber;
import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.SNCheck;

import java.time.LocalDate;

public class ChecksumValidator implements SNCheck {
	@Override
	public ValidationResult validate(ResidentRegistrationNumber rrn) {
		LocalDate birthDate = rrn.getBirthDate();
		int genderCode = rrn.getGenderCode();
		String normalized = rrn.getNormalized();

		if (birthDate == null) return ValidationResult.fail("Birth date missing : " + rrn.getRawInput());
		if (birthDate.isAfter(LocalDate.of(2020, 10, 1))) return ValidationResult.ok();

		int[] multipliers = {2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5};
		int sum = 0;
		for (int i = 0; i < 12; i++) {
			sum += Character.getNumericValue(normalized.charAt(i)) * multipliers[i];
		}
		int mod = (genderCode >= 5) ? (13 - (sum % 11)) % 10 : (11 - (sum % 11)) % 10;

		if (mod != Character.getNumericValue(normalized.charAt(12))) {
			return ValidationResult.fail("checksum mismatch : " + rrn.getRawInput());
		}
		return ValidationResult.ok();
	}
}