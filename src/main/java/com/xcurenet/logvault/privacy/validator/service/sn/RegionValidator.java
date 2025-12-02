package com.xcurenet.logvault.privacy.validator.service.sn;

import com.xcurenet.logvault.privacy.ResidentRegistrationNumber;
import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.SNCheck;

import java.time.LocalDate;

public class RegionValidator implements SNCheck {
	@Override
	public ValidationResult validate(ResidentRegistrationNumber rrn) {
		LocalDate birthDate = rrn.getBirthDate();
		if (birthDate == null) return ValidationResult.fail("Birth date missing : " + rrn.getRawInput());
		if (birthDate.isAfter(LocalDate.of(2020, 10, 1))) return ValidationResult.ok();

		int region = Integer.parseInt(rrn.getNormalized().substring(8, 10));
		if (!KoreanResidentRegion.ALLOWED_CODES.contains(region)) {
			return ValidationResult.fail("Invalid region code : " + rrn.getRawInput());
		}

		int order = Character.getNumericValue(rrn.getNormalized().charAt(11));
		if (order < 1 || order > 6) {
			return ValidationResult.fail("Invalid order number : " + rrn.getRawInput());
		}

		return ValidationResult.ok();
	}
}