package com.xcurenet.logvault.privacy;

import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

@Getter
public class ResidentRegistrationNumber {
	private final String rawInput;
	private final String context;
	private final String normalized;

	private final String front; // YYMMDD
	private final String back;  // GHIJKLX
	private final int genderCode;
	private final LocalDate birthDate;

	public ResidentRegistrationNumber(final String input, final String context) {
		this.rawInput = input == null ? "" : input.trim();
		this.normalized = rawInput.replace("-", "");
		this.context = context;

		if (normalized.length() == 13 && normalized.matches("\\d{13}")) {
			this.front = normalized.substring(0, 6);
			this.back = normalized.substring(6);
			this.genderCode = Character.getNumericValue(normalized.charAt(6));
			this.birthDate = parseBirthDate(front, genderCode);
		} else {
			this.front = "";
			this.back = "";
			this.genderCode = -1;
			this.birthDate = null;
		}
	}

	private LocalDate parseBirthDate(String front, int genderCode) {
		if (front.length() != 6) return null;
		String yy = front.substring(0, 2);
		String mm = front.substring(2, 4);
		String dd = front.substring(4, 6);

		String century;
		switch (genderCode) {
			case 9:
			case 0:
				century = "18";
				break;
			case 1:
			case 2:
			case 5:
			case 6:
				century = "19";
				break;
			case 3:
			case 4:
			case 7:
			case 8:
				century = "20";
				break;
			default:
				return null;
		}

		try {
			LocalDate parsed = LocalDate.parse(century + yy + mm + dd, DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT));
			if (parsed.isAfter(LocalDate.now())) {
				parsed = parsed.minusYears(100);
			}
			return parsed;
		} catch (Exception e) {
			return null;
		}
	}

	public boolean isInValidFormat() {
		return !normalized.matches("\\d{13}");
	}
}
