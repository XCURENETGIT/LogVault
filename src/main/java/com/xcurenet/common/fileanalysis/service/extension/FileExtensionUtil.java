package com.xcurenet.common.fileanalysis.service.extension;

import com.xcurenet.common.utils.Common;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Log4j2
@Component
@RequiredArgsConstructor
public class FileExtensionUtil {
	public static final String UNKNOWN = "unknown";
	private static final byte[][] SIGNATURES = {
			// "<## NASCA DRM FILE"
			new byte[]{(byte) 0x3C, (byte) 0x23, (byte) 0x23, (byte) 0x20, (byte) 0x4E, (byte) 0x41, (byte) 0x53, (byte) 0x43, (byte) 0x41, (byte) 0x20, (byte) 0x44, (byte) 0x52, (byte) 0x4D, (byte) 0x20, (byte) 0x46, (byte) 0x49, (byte) 0x4C, (byte) 0x45}};
	private static final String[] DRM_EXTENSIONS = {"drm", "fasoo", "incops", "nasca", "somansa", "softcamp"};
	private static final String COMMON_DRM_EXT = "drm";
	private final FileCommand fileCommand;
	private final FileExtensionConfig config;

	public Extension getExtension(final File orgFile, final String orgFileName, String expected) {
		if (orgFile == null || !orgFile.exists()) return null;

		if (expected.equals(UNKNOWN)) expected = fileCommand.getExtension(orgFile);

		Extension extension = detection(orgFileName, expected);
		extension.setChangeType(isChangeType(orgFileName, extension));

		boolean drm = isDrm(extension, orgFile);
		extension.setDrm(drm);
		if (drm) {
			// DRM 일 경우 확장자를 unknown으로 바꿔주고 변조여부false
			extension.setExtension(UNKNOWN);
			extension.setChangeType(false);
		}
		return extension;
	}

	private Extension detection(final String orgFileName, final String expected) {
		if (Common.isEmpty(orgFileName)) {
			if (Common.isEmpty(expected)) {
				// 원본 파일명, 예상확장자 둘 다 없으면 unknown
				return new Extension(true, false, UNKNOWN);
			} else {
				// 원본 파일명 없으면 예상확장자 기반으로만 판단
				return new Extension(UNKNOWN.equalsIgnoreCase(expected), isEncrypted("", expected), normalize(expected));
			}
		}

		boolean isUnknown = UNKNOWN.equalsIgnoreCase(expected);
		final String orgExt = FilenameUtils.getExtension(orgFileName).toLowerCase();
		if (isUnknown) {
			if (Common.isEmpty(orgExt)) {
				// 원본확장자가 없으면 unknown
				return new Extension(true, false, UNKNOWN);
			} else {
				// 원본확장자 있으면 원본확장자 신뢰
				return new Extension(true, isEncrypted(orgExt, expected), orgExt);
			}
		}

		final boolean encrypted = isEncrypted(orgExt, expected);
		final String expectedExt = normalize(expected); // toLowerCase + enc_ 제거
		if ("office_zip".equals(expectedExt) && (orgExt.equals("xlsx") || orgExt.equals("pptx") || orgExt.equals("docx"))) {
			return new Extension(false, encrypted, orgExt);
		}

		//  예외: 예상확장자가 txt/zip이면 원본 확장자 신뢰 (zip 인 경우 다른 압축 파일 형식도 다수 있음)
		if ("txt".equals(expectedExt) || "zip".equals(expectedExt)) {
			if (Common.isEmpty(orgExt)) {
				// 원본확장자가 없으면 unknown
				return new Extension(false, encrypted, expectedExt);
			} else {
				// 원본확장자 있으면 원본확장자 신뢰
				return new Extension(false, encrypted, orgExt);
			}
		}

		// 예상확장자 -> 원본 매핑(반전된 파일 형태) 지원
		List<String> mappedFromExpected = config.getMappingMap().get(expectedExt);
		if (mappedFromExpected != null && mappedFromExpected.stream().anyMatch(s -> s.equalsIgnoreCase(orgExt))) {
			return new Extension(false, encrypted, orgExt);
		}

		// 매핑에 근거가 없으면 예상확장자을 신뢰
		return new Extension(false, encrypted, expectedExt);
	}

	/**
	 * 예상확장자 문자열 정규화: null 방지 + 소문자 + enc_ 접두어 제거
	 */
	private String normalize(String expected) {
		if (Common.isEmpty(expected)) return UNKNOWN;
		String s = expected.toLowerCase();
		if (s.startsWith("enc_")) s = s.substring(4);
		return s;
	}

	private boolean isChangeType(final String orgFileName, final Extension extension) {
		if (Common.isEmpty(orgFileName)) return false;
		if (extension.isUnknown()) return false;

		String name = orgFileName.toLowerCase();
		String orgExt = FilenameUtils.getExtension(name).toLowerCase();
		String detectedExt = extension.getExtension().toLowerCase();
		// 암호화 걸린 문서파일의 매직넘버
		if ("d0cf".equalsIgnoreCase(orgExt) && extension.isEncrypted) return false;
		if (matchesSplit(detectedExt, name, orgExt)) return false;
		return !orgExt.equalsIgnoreCase(detectedExt);
	}

	private boolean matchesSplit(String canonicalDetected, String fileNameLower, String orgExtLower) {
		List<Pattern> extPats = config.getSplitExtPatterns().get(canonicalDetected);
		if (extPats != null && extPats.stream().anyMatch(p -> p.matcher(orgExtLower).matches())) {
			return true;
		}
		List<Pattern> namePats = config.getSplitNamePatterns().get(canonicalDetected);
		return namePats != null && namePats.stream().anyMatch(p -> p.matcher(fileNameLower).matches());
	}

	private boolean isEncrypted(final String orgExt, final String expected) {
		// 암호화 걸린 문서파일의 매직넘버
		if ("d0cf".equalsIgnoreCase(orgExt)) return true;
		if (Common.isEmpty(expected)) return false;
		else return expected.startsWith("enc_");
	}

	private boolean isDrm(Extension extension, File file) {
		String ext = extension.getExtension().toLowerCase();
		if (extension.isUnknown()) {// 확장자가 unknown 이라면 file 을 직접 열어서 확인
			boolean isDrm = hasDrmSignature(file);
			log.debug("IS_DRM_S | {} | {}", isDrm, file.getAbsolutePath());
			return isDrm;
		} else {// 확장자로 판단
			boolean isDrm = Arrays.asList(DRM_EXTENSIONS).contains(ext);
			log.debug("IS_DRM_E | {} | {}", ext, isDrm);
			return isDrm;
		}
	}

	public static boolean hasDrmSignature(File file) {
		try (FileInputStream fis = new FileInputStream(file)) {
			int maxLen = 0;
			for (byte[] sig : SIGNATURES) {
				if (sig.length > maxLen) {
					maxLen = sig.length;
				}
			}

			byte[] buffer = new byte[maxLen];
			int read = fis.read(buffer);
			if (read <= 0) {
				return false;
			}

			// 각 시그니처와 비교
			for (byte[] sig : SIGNATURES) {
				if (read >= sig.length && startsWith(buffer, sig)) {
					return true;
				}
			}
			return false;

		} catch (IOException e) {
			log.error("DRM__ERR | {} | {}", file.getAbsolutePath(), e);
			return false;
		}
	}

	private static boolean startsWith(byte[] data, byte[] prefix) {
		for (int i = 0; i < prefix.length; i++) {
			if (data[i] != prefix[i]) return false;
		}
		return true;
	}

	@Data
	public static class Extension {
		private boolean isUnknown;    // 예상 확장자 알수 없음
		private boolean isEncrypted;  // 파일 암호 여부
		private String extension;     //예상 확장자
		private boolean isChangeType; // 확장자 변조
		private boolean isDrm;        // DRM 여부

		public Extension(boolean isUnknown, boolean isEncrypted, String extension) {
			this.isUnknown = isUnknown;
			this.isEncrypted = isEncrypted;
			this.extension = extension;
		}

		public boolean isExcel() {
			return extension.equals("ods") || extension.equals("xlsx") || extension.equals("cell") || extension.equals("xls");
		}
	}

}