package com.xcurenet.logvault.privacy.validator.service.sn;

import com.xcurenet.logvault.privacy.ResidentRegistrationNumber;
import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.SNCheck;

import java.time.LocalDate;

public class GenderValidator implements SNCheck {
	@Override
	public ValidationResult validate(ResidentRegistrationNumber rrn) {
		int genderCode = rrn.getGenderCode();
		LocalDate birthDate = rrn.getBirthDate();
		if (genderCode < 0 || genderCode > 8) {
			return ValidationResult.fail("Invalid gender code : " + rrn.getRawInput());
		}
		if (birthDate == null) {
			return ValidationResult.fail("Birth date missing : " + rrn.getRawInput());
		}

		int year = birthDate.getYear();
		if (year >= 1900 && year <= 1999 && !(genderCode == 1 || genderCode == 2 || genderCode == 5 || genderCode == 6)) {
			return ValidationResult.fail("Gender code mismatch for 1900s : " + rrn.getRawInput());
		}
		if (year >= 2000 && year <= 2099 && !(genderCode == 3 || genderCode == 4 || genderCode == 7 || genderCode == 8)) {
			return ValidationResult.fail("Gender code mismatch for 2000s : " + rrn.getRawInput());
		}
		// 1800년대는 무조건 유효하지 않음
		if (year >= 1800 && year <= 1899) {
			return ValidationResult.fail("1800s birth year is not valid : " + rrn.getRawInput());
		}
		return ValidationResult.ok();
	}
}