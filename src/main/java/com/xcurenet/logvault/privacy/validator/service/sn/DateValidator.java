package com.xcurenet.logvault.privacy.validator.service.sn;

import com.xcurenet.logvault.privacy.ResidentRegistrationNumber;
import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.SNCheck;

import java.time.LocalDate;

public class DateValidator implements SNCheck {
	@Override
	public ValidationResult validate(ResidentRegistrationNumber rrn) {
		LocalDate birthDate = rrn.getBirthDate();
		if (birthDate == null) return ValidationResult.fail("Invalid birth date : " + rrn.getRawInput());
		if (birthDate.isAfter(LocalDate.now()))
			return ValidationResult.fail("Birth date is in the future : " + rrn.getRawInput());

		int age = LocalDate.now().getYear() - birthDate.getYear();
		if (LocalDate.now().isBefore(birthDate.plusYears(age))) age--;
		if (age < 0 || age > 100) return ValidationResult.fail("Age not in valid range (0~100) : " + rrn.getRawInput());

		return ValidationResult.ok();
	}
}
