package com.xcurenet.logvault.conf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Config - 설정값 검증")
class ConfigTest {

	private Config config;

	@BeforeEach
	void setUp() {
		config = new Config();
	}

	@Nested
	@DisplayName("getOcrTargetExt() - OCR 대상 확장자")
	class OcrTargetExt {
		@Test
		@DisplayName("LC 타입일 때 gif 포함 (오타 수정 검증)")
		void lc_shouldContainGif() {
			ReflectionTestUtils.setField(config, "ocrApiType", "LC");
			Set<String> ext = config.getOcrTargetExt();
			assertTrue(ext.contains("gif"), "LC 모드에서 gif 확장자가 포함되어야 함");
			assertFalse(ext.contains("git"), "git은 오타이므로 포함되면 안 됨");
		}

		@Test
		@DisplayName("LC 타입 기본 확장자 목록 검증")
		void lc_shouldContainAllDefaults() {
			ReflectionTestUtils.setField(config, "ocrApiType", "LC");
			Set<String> ext = config.getOcrTargetExt();
			assertAll(() -> assertTrue(ext.contains("jpg")), () -> assertTrue(ext.contains("jpeg")), () -> assertTrue(ext.contains("png")), () -> assertTrue(ext.contains("bmp")), () -> assertTrue(ext.contains("tiff")), () -> assertTrue(ext.contains("webp")), () -> assertTrue(ext.contains("gif")), () -> assertTrue(ext.contains("pdf")));
			assertEquals(8, ext.size());
		}

		@Test
		@DisplayName("SY 타입일 때 프로퍼티 기반 확장자 반환")
		void sy_shouldReturnPropertyValue() {
			ReflectionTestUtils.setField(config, "ocrApiType", "SY");
			Set<String> propSet = Set.of("tiff", "png", "pdf");
			ReflectionTestUtils.setField(config, "ocrTargetExt", propSet);
			assertSame(propSet, config.getOcrTargetExt());
		}
	}

	@Nested
	@DisplayName("getInterval() - 파일 대기 시간")
	class Interval {
		@Test
		@DisplayName("fileWaitTime(초) × 1000 = 밀리초")
		void shouldConvertToMillis() {
			ReflectionTestUtils.setField(config, "fileWaitTime", 600);
			assertEquals(600_000, config.getInterval());
		}

		@Test
		@DisplayName("fileWaitTime=0 → 0ms")
		void zero_shouldReturnZero() {
			ReflectionTestUtils.setField(config, "fileWaitTime", 0);
			assertEquals(0, config.getInterval());
		}
	}

	@Nested
	@DisplayName("getPath() - 디코더 데이터 경로 생성")
	class GetPath {
		@Test
		@DisplayName("정상 파일명 → split dir 기반 경로 생성")
		void validFileName_shouldBuildPath() {
			ReflectionTestUtils.setField(config, "dataPath", "/users/las/msg/data");
			ReflectionTestUtils.setField(config, "decoderSplitDir", 100);
			String path = config.getPath("testfile.hdr", false);
			assertNotNull(path);
			assertTrue(path.startsWith("/users/las/msg/data"));
			assertTrue(path.endsWith("testfile.hdr"));
		}

		@Test
		@DisplayName("null 파일명 → null 반환")
		void nullFileName_shouldReturnNull() {
			assertNull(config.getPath(null, false));
		}

		@Test
		@DisplayName("빈 파일명 → null 반환")
		void emptyFileName_shouldReturnNull() {
			assertNull(config.getPath("", false));
		}
	}

	@Nested
	@DisplayName("경로 축약 메서드")
	class PathSmall {
		@Test
		@DisplayName("getWmailPathSmall - wmail 디렉터리 이후 경로만 추출")
		void wmailPathSmall() {
			ReflectionTestUtils.setField(config, "dirWmail", "/users/las/msg/info/wmail");
			String result = config.getWmailPathSmall("/users/las/msg/info/wmail/sub/test.msg");
			assertEquals("/sub/test.msg", result);
		}

		@Test
		@DisplayName("getWmailPathSmall - wmail 경로 미포함 시 원본 반환")
		void wmailPathSmall_noMatch() {
			ReflectionTestUtils.setField(config, "dirWmail", "/users/las/msg/info/wmail");
			String result = config.getWmailPathSmall("/other/path/test.msg");
			assertEquals("/other/path/test.msg", result);
		}

		@Test
		@DisplayName("getDestPathSmall - attachRoot 이후 경로만 추출")
		void destPathSmall() {
			ReflectionTestUtils.setField(config, "attachRoot", "/data01/attach");
			String result = config.getDestPathSmall("/data01/attach/20251104/1510/msgid/file.zip");
			assertEquals("/20251104/1510/msgid/file.zip", result);
		}
	}
}
