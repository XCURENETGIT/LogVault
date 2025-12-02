package com.xcurenet.logvault.privacy.validator.service.cpn;

import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

import java.util.Set;

public class DigitValidator implements DefaultCheck {
	private static final Set<String> VALID_REGISTRY_CODES = Set.of(
			// ---------------- 서울 ----------------
			"1101", "1103", "2401", "2501", "2601", "2701",
			// ---------------- 의정부 ----------------
			"2802", "2841", "2842", "2843", "2844", "2845", "2846", "2847", "2849", "2850",
			// ---------------- 인천 ----------------
			"1201", "1242", "1244", "1245",
			// ---------------- 수원 ----------------
			"1341", "1342", "1343", "1344", "1345", "1346", "1347", "1348", "1349", "1350", "1354", "1355", "1356", "1357", "1358", "1359",
			// ---------------- 춘천 ----------------
			"1401", "1402", "1411", "1412", "1413", "1441", "1442", "1443", "1444", "1445", "1446", "1447", "1448", "1449", "1450", "1451", "1452",
			// ---------------- 청주 ----------------
			"1501", "1511", "1512", "1513", "1514", "1542", "1543", "1544", "1545",
			// ---------------- 대전 ----------------
			"1601", "1611", "1612", "1613", "1614", "1615", "1641", "1642", "1643", "1644", "1647", "1648", "1649", "1650", "1651", "1652",
			// ---------------- 대구 ----------------
			"1701", "1711", "1712", "1713", "1714", "1715", "1716", "1717", "1718", "1743", "1744", "1745", "1747", "1748", "1749", "1750", "1751", "1752", "1754", "1755", "1756", "1757", "1758", "1759", "1760",
			// ---------------- 부산 ----------------
			"1801", "1811", "1849", "1841", "1843", "1844", "1847", "1851",
			// ---------------- 울산 ----------------
			"2301", "2341",
			// ---------------- 창원 ----------------
			"1942", "1915", "1911", "1912", "1913", "1914", "1941", "1943", "1944", "1945", "1946", "1947", "1948", "1949", "1950", "1951", "1952", "1953", "1954", "1955",
			// ---------------- 광주 ----------------
			"2001", "2011", "2012", "2013", "2014", "2043", "2044", "2045", "2046", "2047", "2049", "2050", "2052", "2053", "2054", "2055", "2056", "2057", "2058", "2059", "2060", "2061", "2062",
			// ---------------- 전주 ----------------
			"2111", "2112", "2113", "2141", "2142", "2143", "2144", "2145", "2146", "2147", "2148", "2149", "2101",
			// ---------------- 제주 ----------------
			"2201", "2241");


	@Override
	public ValidationResult validate(final String input, final String context) {
		if (input == null) {
			return ValidationResult.fail("Corporate registration number is null");
		}
		String digits = input.replaceAll("[^0-9]", ""); // 숫자만 추출
		if (!digits.matches("\\d{13}")) {
			return ValidationResult.fail("Corporate registration number must be 13 digits : " + input);
		}

		// 앞 6자리 (등기소 코드)
		String registry = digits.substring(0, 6);
		if (registry.equals("000000") || registry.equals("999999")) {
			return ValidationResult.fail("Invalid registry office code : " + input);
		}

		String registry2 = digits.substring(0, 4);
		if (!VALID_REGISTRY_CODES.contains(registry2)) {
			return ValidationResult.fail("Unknown registry office code : " + input);
		}

		// 뒤 6자리 (일련번호)
		String serial = digits.substring(6, 12);
		if (serial.equals("000000")) {
			return ValidationResult.fail("Invalid serial number (all zeros) : " + input);
		}
		if (serial.matches("9{6}")) {
			return ValidationResult.fail("Invalid serial number (test range) : " + input);
		}

		// 비정상적인 패턴 차단
		if (digits.chars().distinct().count() == 1) {
			return ValidationResult.fail("Invalid repetitive number : " + input);
		}
		return ValidationResult.ok();
	}
}
