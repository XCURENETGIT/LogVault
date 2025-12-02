package com.xcurenet.logvault.privacy.validator.service.sn;

import java.util.HashSet;
import java.util.Set;

public class KoreanResidentRegion {
	public static final Set<Integer> ALLOWED_CODES = new HashSet<>();

	static {
		String[][] REGION_CODES = {{"서울특별시", "00", "08"}, {"부산광역시", "09", "12"}, {"인천광역시", "14", "15"}, {"경기도", "16", "25"}, {"강원도", "26", "34"}, {"충청북도", "35", "39"}, {"대전광역시", "40", "41"}, {"충청남도", "42", "43"}, {"충청남도", "45", "47"}, {"세종특별자치시", "44", "44"}, {"세종특별자치시", "96", "96"}, {"전라북도", "48", "54"}, {"전라남도", "55", "64"}, {"광주광역시", "55", "56"}, {"광주광역시", "65", "66"}, {"대구광역시", "67", "69"}, {"대구광역시", "76", "77"}, {"경상북도", "70", "75"}, {"경상북도", "77", "81"}, {"경상남도", "82", "84"}, {"경상남도", "86", "93"}, {"울산광역시", "85", "85"}, {"울산광역시", "90", "90"}, {"제주특별자치도", "93", "95"}};
		for (String[] entry : REGION_CODES) {
			int start = Integer.parseInt(entry[1]);
			int end = Integer.parseInt(entry[2]);
			for (int code = start; code <= end; code++) {
				ALLOWED_CODES.add(code);
			}
		}
	}
}