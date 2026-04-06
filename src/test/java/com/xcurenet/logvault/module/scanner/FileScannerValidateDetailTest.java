package com.xcurenet.logvault.module.scanner;

import com.xcurenet.logvault.exception.ScanException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileScanner.validateDetail() - MSG 파일명 검증")
class FileScannerValidateDetailTest {

	private static final String VALID = "WMAIL20251104151028-01e13165-d8ef2415-57793-443-00-462358-DEBDA8FBC3951135-VI01.msg";

	@Nested
	@DisplayName("정상 케이스")
	class Valid {
		@Test
		@DisplayName("정상 파일명 통과")
		void valid() {
			assertDoesNotThrow(() -> FileScanner.validateDetail(VALID));
		}

		@Test
		@DisplayName("포트 0 경계값 통과")
		void portZero() {
			assertDoesNotThrow(() -> FileScanner.validateDetail("WMAIL20251104151028-01e13165-d8ef2415-0-0-00-462358-HOST1-HOST2.msg"));
		}

		@Test
		@DisplayName("포트 65535 경계값 통과")
		void portMax() {
			assertDoesNotThrow(() -> FileScanner.validateDetail("WMAIL20251104151028-01e13165-d8ef2415-65535-65535-00-462358-HOST1-HOST2.msg"));
		}

		@Test
		@DisplayName("Hex IP 대소문자 혼용 통과")
		void hexMixedCase() {
			assertDoesNotThrow(() -> FileScanner.validateDetail("WMAIL20251104151028-aAbBcCdD-11223344-1000-443-00-1-HOST1-HOST2.msg"));
		}
	}

	@Nested
	@DisplayName("파일명 기본 검증 실패")
	class BasicFail {
		@ParameterizedTest
		@NullAndEmptySource
		@DisplayName("null 또는 빈 문자열 → ScanException")
		void nullOrEmpty(String name) {
			assertThrows(ScanException.class, () -> FileScanner.validateDetail(name));
		}

		@Test
		@DisplayName("확장자 없음 → ScanException")
		void noExtension() {
			assertThrows(ScanException.class, () -> FileScanner.validateDetail("WMAIL20251104151028"));
		}

		@Test
		@DisplayName("파트 수 부족 (9개 미만) → ScanException")
		void insufficientParts() {
			assertThrows(ScanException.class, () -> FileScanner.validateDetail("WMAIL20251104151028-01e13165-d8ef2415-443-00.msg"));
		}
	}

	@Nested
	@DisplayName("WMAIL 헤더 검증")
	class Header {
		@Test
		@DisplayName("WMAIL 접두사 없음 → ScanException")
		void wrongPrefix() {
			assertThrows(ScanException.class, () -> FileScanner.validateDetail("XMAIL20251104151028-01e13165-d8ef2415-57793-443-00-462358-HOST1-HOST2.msg"));
		}

		@Test
		@DisplayName("날짜 부분 비숫자 → ScanException")
		void nonNumericDate() {
			assertThrows(ScanException.class, () -> FileScanner.validateDetail("WMAILabcdefghijklmn-01e13165-d8ef2415-57793-443-00-462358-HOST1-HOST2.msg"));
		}

		@Test
		@DisplayName("헤더 길이 19자 미만 → ScanException")
		void shortHeader() {
			assertThrows(ScanException.class, () -> FileScanner.validateDetail("WMAIL2025110415-01e13165-d8ef2415-57793-443-00-462358-HOST1-HOST2.msg"));
		}
	}

	@Nested
	@DisplayName("IP Hex 검증")
	class Hex {
		@Test
		@DisplayName("Source IP Non-Hex → ScanException")
		void srcNonHex() {
			assertThrows(ScanException.class, () -> FileScanner.validateDetail("WMAIL20251104151028-ZZZZZZZZ-d8ef2415-57793-443-00-462358-HOST1-HOST2.msg"));
		}

		@Test
		@DisplayName("Dest IP Non-Hex → ScanException")
		void dstNonHex() {
			assertThrows(ScanException.class, () -> FileScanner.validateDetail("WMAIL20251104151028-01e13165-GGGGGGGG-57793-443-00-462358-HOST1-HOST2.msg"));
		}
	}

	@Nested
	@DisplayName("포트 범위 검증")
	class Port {
		@Test
		@DisplayName("Source Port > 65535 → ScanException")
		void srcOverflow() {
			assertThrows(ScanException.class, () -> FileScanner.validateDetail("WMAIL20251104151028-01e13165-d8ef2415-65536-443-00-462358-HOST1-HOST2.msg"));
		}

		@Test
		@DisplayName("비숫자 포트 → ScanException")
		void nonNumericPort() {
			assertThrows(ScanException.class, () -> FileScanner.validateDetail("WMAIL20251104151028-01e13165-d8ef2415-abc-443-00-462358-HOST1-HOST2.msg"));
		}
	}

	@Nested
	@DisplayName("시퀀스/호스트 검증")
	class SeqHost {
		@Test
		@DisplayName("Seq1 비숫자 → ScanException")
		void nonNumericSeq() {
			assertThrows(ScanException.class, () -> FileScanner.validateDetail("WMAIL20251104151028-01e13165-d8ef2415-57793-443-AB-462358-HOST1-HOST2.msg"));
		}

		@Test
		@DisplayName("Host1 빈값 → ScanException")
		void emptyHost1() {
			assertThrows(ScanException.class, () -> FileScanner.validateDetail("WMAIL20251104151028-01e13165-d8ef2415-57793-443-00-462358--HOST2.msg"));
		}

		@Test
		@DisplayName("Host2 빈값 → ScanException")
		void emptyHost2() {
			assertThrows(ScanException.class, () -> FileScanner.validateDetail("WMAIL20251104151028-01e13165-d8ef2415-57793-443-00-462358-HOST1-.msg"));
		}
	}
}
