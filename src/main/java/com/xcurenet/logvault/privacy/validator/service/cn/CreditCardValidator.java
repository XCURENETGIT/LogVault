package com.xcurenet.logvault.privacy.validator.service.cn;

import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class CreditCardValidator implements DefaultCheck {
	public static enum CreditCard {
		INVALID(null),
		AMERICAN_EXPRESS("American Express"),
		CHINA_UNIONPAY("China UnionPay"),
		DINERS_CLUB_CARTE_BLANCHE("Diners Club Carte Blanche"),
		DINERS_CLUB_INTERNATIONAL("Diners Club International"),
		DINERS_CLUB_US_AND_CANADA("Diners Club US & Canada"),
		DISCOVER_CARD("Discover Card"),
		JCB("JCB"),
		LASER("Laser"),
		MAESTRO("Maestro"),
		MASTERCARD("MasterCard"),
		SOLO("Solo"),
		SWITCH("Switch"),
		VISA("Visa"),
		VISA_ELECTRON("Visa Electron"),
		KOREA_ONLY("Korea Only"),
		BC_GLOBAL("BC Global");

		public final String name;

		CreditCard(final String name) {
			this.name = name;
		}
	}

	@Override
	public ValidationResult validate(String input, String context) {
		if (isLengthAndPrefixCorrect(input)) return ValidationResult.ok();
		return ValidationResult.fail("Invalid credit card number " + input);
	}

	/**
	 * Checks if the credit card number can be determined as a valid number.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return <code>TRUE</code> if the credit card number could be determined as a
	 * valid number, else <code>FALSE</code> is returned
	 */
	protected boolean isLengthAndPrefixCorrect(String creditCardNumber) {
		if (creditCardNumber == null) {
			return false;
		}
		// strip spaces and dashes
		creditCardNumber = creditCardNumber.replaceAll("[ -]", "");
		// the length of the credit card number has to be between 12 and 19.
		// else the number is invalid.
		if ((creditCardNumber.length() >= 12) && (creditCardNumber.length() <= 19) && isChecksumCorrect(creditCardNumber)) {
			return (determineCardId(creditCardNumber) != CreditCard.INVALID);
		}
		return false;
	}

	/**
	 * Checks if the credit card number can be determined as a valid number.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return <code>TRUE</code> if the credit card number could be determined as a
	 * valid number, else <code>FALSE</code> is returned
	 */
	public final CreditCard determineCardId(String creditCardNumber) {
		if (creditCardNumber == null) {
			return CreditCard.INVALID;
		}

		// strip spaces and dashes
		creditCardNumber = creditCardNumber.replaceAll("[ -]", "");

		// the length of the credit card number has to be between 12 and 19.
		// else the number is invalid.
		CreditCard cardId;
		if ((creditCardNumber.length() >= 12) && (creditCardNumber.length() <= 19) && isChecksumCorrect(creditCardNumber)) {
			cardId = isKoreaOnly(creditCardNumber);
			if (cardId == CreditCard.INVALID) {
				cardId = isBCGlobal(creditCardNumber);
			}
			if (cardId == CreditCard.INVALID) {
				cardId = isVisa(creditCardNumber);
			}
			if (cardId == CreditCard.INVALID) {
				cardId = isMastercard(creditCardNumber);
			}

			if (cardId == CreditCard.INVALID) {
				cardId = isAmericanExpress(creditCardNumber);
			}
			if (cardId == CreditCard.INVALID) {
				cardId = isChinaUnionPay(creditCardNumber);
			}
			if (cardId == CreditCard.INVALID) {
				cardId = isDinersClubCarteBlanche(creditCardNumber);
			}
			if (cardId == CreditCard.INVALID) {
				cardId = isDinersClubInternational(creditCardNumber);
			}
			if (cardId == CreditCard.INVALID) {
				cardId = isDinersClubUsAndCanada(creditCardNumber);
			}
			if (cardId == CreditCard.INVALID) {
				cardId = isDiscoverCard(creditCardNumber);
			}
			if (cardId == CreditCard.INVALID) {
				cardId = isJCB(creditCardNumber);
			}
			if (cardId == CreditCard.INVALID) {
				cardId = isLaser(creditCardNumber);
			}
			if (cardId == CreditCard.INVALID) {
				cardId = isMaestro(creditCardNumber);
			}
			if (cardId == CreditCard.INVALID) {
				cardId = isSolo(creditCardNumber);
			}
			if (cardId == CreditCard.INVALID) {
				cardId = isSwitch(creditCardNumber);
			}
			if (cardId == CreditCard.INVALID) {
				cardId = isVisaElectron(creditCardNumber);
			}
		} else {
			cardId = isUnknown(creditCardNumber);
		}
		return cardId;
	}


	/**
	 * Can be used (subclassed) to extend the test with a credit card not yet known
	 * by the validator.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	protected CreditCard isUnknown(final String creditCardNumber) {
		return CreditCard.INVALID;
	}

	private CreditCard isKoreaOnly(final String creditCardNumber) {
		if (creditCardNumber.length() == 16 && creditCardNumber.startsWith("9")) {
			if (creditCardNumber.startsWith("90") || creditCardNumber.startsWith("94") || creditCardNumber.startsWith("95")) {
				return CreditCard.KOREA_ONLY;
			}
		}

		return CreditCard.INVALID;
	}

	private CreditCard isBCGlobal(final String creditCardNumber) {
		if (creditCardNumber.length() == 16) {
			final int firstDigits = Integer.parseInt(creditCardNumber.substring(0, 8));
			if ((firstDigits >= 65730300 && firstDigits <= 65735099) || (firstDigits >= 65410300 && firstDigits <= 65415099) || (firstDigits >= 65420300 && firstDigits <= 65425099) || (firstDigits >= 65430300 && firstDigits <= 65432099) || (firstDigits >= 65432200 && firstDigits <= 65435099) || (firstDigits >= 65560300 && firstDigits <= 65565099) || (firstDigits >= 65580300 && firstDigits <= 65585099) || (firstDigits >= 65590300 && firstDigits <= 65595099)) {
				return CreditCard.BC_GLOBAL;
			}
		}

		return CreditCard.INVALID;
	}

	/**
	 * Check if the credit card is an American Express. An American Express number
	 * has to start with 34 or 37 and has to have a length of 15. The number has to
	 * be validated with the Luhn algorithm.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	private CreditCard isAmericanExpress(final String creditCardNumber) {
		if (creditCardNumber.length() == 15 && (creditCardNumber.startsWith("34") || creditCardNumber.startsWith("37"))) {
			return CreditCard.AMERICAN_EXPRESS;
		}

		return CreditCard.INVALID;
	}

	/**
	 * Check if the credit card is a China UnionPay. A China UnionPay number has to
	 * start with 622 (622126-622925) and has to have a length between 16 and 19. No
	 * further validation takes place.<br/>
	 * <br/>
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	private CreditCard isChinaUnionPay(final String creditCardNumber) {
		if ((creditCardNumber.length() >= 16 && creditCardNumber.length() <= 19) && (creditCardNumber.startsWith("622"))) {
			final int firstDigits = Integer.parseInt(creditCardNumber.substring(0, 5));
			if (firstDigits >= 622126 && firstDigits <= 622925) {
				return CreditCard.CHINA_UNIONPAY;
			}
		}

		return CreditCard.INVALID;
	}

	/**
	 * Check if the credit card is a Diners Club Carte Blanche. A Diners Club Carte
	 * Blanche number has to start with a number between 300 and 305 and has to have
	 * a length of 14. The number has to be validated with the Luhn algorithm.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	private CreditCard isDinersClubCarteBlanche(final String creditCardNumber) {
		if (creditCardNumber.length() == 14 && creditCardNumber.startsWith("30")) {
			final int firstDigits = Integer.parseInt(creditCardNumber.substring(0, 3));
			if (firstDigits >= 300 && firstDigits <= 305) {
				return CreditCard.DINERS_CLUB_CARTE_BLANCHE;
			}
		}

		return CreditCard.INVALID;
	}

	/**
	 * Check if the credit card is a Diners Club International. A Diners Club
	 * International number has to start with the number 36 and has to have a length
	 * of 14. The number has to be validated with the Luhn algorithm.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	private CreditCard isDinersClubInternational(final String creditCardNumber) {
		if (creditCardNumber.length() == 14 && creditCardNumber.startsWith("36")) {
			return CreditCard.DINERS_CLUB_INTERNATIONAL;
		}
		return CreditCard.INVALID;
	}

	/**
	 * Check if the credit card is a Diners Club US & Canada. A Diners Club US &
	 * Canada number has to start with the number 54 or 55 and has to have a length
	 * of 16. The number has to be validated with the Luhn algorithm.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	private CreditCard isDinersClubUsAndCanada(final String creditCardNumber) {
		if (creditCardNumber.length() == 16 && (creditCardNumber.startsWith("54") || creditCardNumber.startsWith("55"))) {
			return CreditCard.DINERS_CLUB_US_AND_CANADA;
		}
		return CreditCard.INVALID;
	}

	/**
	 * Check if the credit card is a Discover Card. A Discover Card number has to
	 * start with 6011, 622126-622925, 644-649 or 65 and has to have a length of 16.
	 * The number has to be validated with the Luhn algorithm.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	private CreditCard isDiscoverCard(final String creditCardNumber) {
		if (creditCardNumber.length() == 16 && creditCardNumber.startsWith("6")) {
			final int firstThreeDigits = Integer.parseInt(creditCardNumber.substring(0, 3));
			final int firstSixDigits = Integer.parseInt(creditCardNumber.substring(0, 6));
			if (creditCardNumber.startsWith("6011") || creditCardNumber.startsWith("65") || (firstThreeDigits >= 644 && firstThreeDigits <= 649) || (firstSixDigits >= 622126 && firstSixDigits <= 622925)) {
				return CreditCard.DISCOVER_CARD;
			}
		}
		return CreditCard.INVALID;
	}

	/**
	 * Check if the credit card is a JCB. A JCB number has to start with a number
	 * between 3528 and 3589 and has to have a length of 16. The number has to be
	 * validated with the Luhn algorithm.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	private CreditCard isJCB(final String creditCardNumber) {
		if (creditCardNumber.length() == 16) {
			final int firstFourDigits = Integer.parseInt(creditCardNumber.substring(0, 4));
			if (firstFourDigits >= 3528 && firstFourDigits <= 3589) {
				return CreditCard.JCB;
			}
		}
		return CreditCard.INVALID;
	}

	/**
	 * Check if the credit card is a Laser. A Laser number has to start with 6304,
	 * 6706, 6771 or 6709 and has to have a length between 16 and 19 digits. The
	 * number has to be validated with the Luhn algorithm.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	private CreditCard isLaser(final String creditCardNumber) {
		if (creditCardNumber.length() >= 16 && creditCardNumber.length() <= 19) {
			if (creditCardNumber.startsWith("6304") || creditCardNumber.startsWith("6706") || creditCardNumber.startsWith("6771") || creditCardNumber.startsWith("6709")) {
				return CreditCard.LASER;
			}
		}
		return CreditCard.INVALID;
	}

	/**
	 * Check if the credit card is a Maestro. A Maestro number has to start with
	 * 5018,5020,5038,6304,6759,6761 or 6763 and has to have a length between 12 and
	 * 19 digits. The number has to be validated with the Luhn algorithm.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	private CreditCard isMaestro(final String creditCardNumber) {
		if (creditCardNumber.length() >= 12 && creditCardNumber.length() <= 19) {
			if (creditCardNumber.startsWith("5018") || creditCardNumber.startsWith("5020") || creditCardNumber.startsWith("5021") || creditCardNumber.startsWith("5038") || creditCardNumber.startsWith("6304") || creditCardNumber.startsWith("6759") || creditCardNumber.startsWith("6761") || creditCardNumber.startsWith("6763")) {
				return CreditCard.MAESTRO;
			}
		}
		return CreditCard.INVALID;
	}

	/**
	 * Check if the credit card is a Solo. A Solo number has to start with 6334 or
	 * 6767 and has to have a length of 16, 18 or 19 digits. The number has to be
	 * validated with the Luhn algorithm.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	private CreditCard isSolo(final String creditCardNumber) {
		if ((creditCardNumber.length() == 16) || (creditCardNumber.length() == 18) || (creditCardNumber.length() == 19)) {
			if (creditCardNumber.startsWith("6334") || creditCardNumber.startsWith("6767")) {
				return CreditCard.SOLO;
			}
		}
		return CreditCard.INVALID;
	}

	/**
	 * Check if the credit card is a Switch. A Switch number has to start with
	 * 4903,4905,4911,4936,564182,633110,6333 or 6759 and has to have a length of
	 * 16, 18 or 19 digits. The number has to be validated with the Luhn algorithm.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	private CreditCard isSwitch(final String creditCardNumber) {
		if ((creditCardNumber.length() == 16 || creditCardNumber.length() == 18 || creditCardNumber.length() == 19)) {
			if (creditCardNumber.startsWith("4903") || creditCardNumber.startsWith("4905") || creditCardNumber.startsWith("4911") || creditCardNumber.startsWith("4936") || creditCardNumber.startsWith("564182") || creditCardNumber.startsWith("633110") || creditCardNumber.startsWith("6333") || creditCardNumber.startsWith("6759")) {
				return CreditCard.SWITCH;
			}
		}
		return CreditCard.INVALID;
	}

	/**
	 * Check if the credit card is a Visa. A Visa number has to start with a 4 and
	 * has to have a length of 13 or 16 digits. The number has to be validated with
	 * the Luhn algorithm.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	private CreditCard isVisa(final String creditCardNumber) {
		if (creditCardNumber.length() == 13 || creditCardNumber.length() == 16) {
			if (creditCardNumber.startsWith("4")) {
				return CreditCard.VISA;
			}
		}
		return CreditCard.INVALID;
	}

	/**
	 * Check if the credit card is a Visa Electron. A Visa Electron number has to
	 * start with 417500,4917,4913,4508 or 4844 and has to have a length of 16
	 * digits. The number has to be validated with the Luhn algorithm.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	private CreditCard isVisaElectron(final String creditCardNumber) {
		if (creditCardNumber.length() == 16 && (creditCardNumber.startsWith("417500") || creditCardNumber.startsWith("4917") || creditCardNumber.startsWith("4913") || creditCardNumber.startsWith("4508") || creditCardNumber.startsWith("4844"))) {
			return CreditCard.VISA_ELECTRON;
		}
		return CreditCard.INVALID;
	}

	/**
	 * Check if the credit card is a Mastercard. A Mastercard number has to start
	 * with a number between 51 and 55 and has to have a length of 16. The number
	 * has to be validated with the Luhn algorithm.
	 *
	 * @param creditCardNumber the credit card number as a string
	 * @return The credit card id of the issuer
	 */
	private CreditCard isMastercard(final String creditCardNumber) {
		if (creditCardNumber.length() == 16) {
			final int firstTwoDigits = Integer.parseInt(creditCardNumber.substring(0, 2));
			if (firstTwoDigits >= 51 && firstTwoDigits <= 55) {
				return CreditCard.MASTERCARD;
			}
		}
		return CreditCard.INVALID;
	}

	/**
	 * Calculates the checksum of a credit card number using the Luhn algorithm (the
	 * so-called "mod 10" algorithm).
	 *
	 * @param creditCardNumber the credit card number for which the checksum should
	 *                         be calculated
	 * @return <code>TRUE</code> if the checksum for the given credit card number is
	 * valid, else return <code>FALSE</code>
	 * @see <a href="http://en.wikipedia.org/wiki/Luhn_algorithm">Wikipedie - Luhn
	 * algorithm</a>
	 */
	protected final boolean isChecksumCorrect(final String creditCardNumber) {
		final int nulOffset = '0';
		int sum = 0;
		for (int i = 1; i <= creditCardNumber.length(); i++) {
			int currentDigit = creditCardNumber.charAt(creditCardNumber.length() - i) - nulOffset;
			if ((i % 2) == 0) {
				currentDigit *= 2;
				currentDigit = currentDigit > 9 ? currentDigit - 9 : currentDigit;
				sum += currentDigit;
			} else {
				sum += currentDigit;
			}
		}
		return (sum % 10) == 0;
	}
}
