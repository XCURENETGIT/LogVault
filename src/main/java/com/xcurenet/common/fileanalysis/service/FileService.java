package com.xcurenet.common.fileanalysis.service;

import com.xcurenet.common.fileanalysis.service.extension.FileExtensionUtil;
import com.xcurenet.common.fileanalysis.service.extension.excel.SheetDetector;
import com.xcurenet.common.fileanalysis.service.option.Options;
import com.xcurenet.common.fileanalysis.service.text.TextFilter;
import com.xcurenet.common.fileanalysis.service.text.TextFilterResult;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.CompressUtil;
import com.xcurenet.logvault.conf.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class FileService {
	private final TextFilter textFilter;
	private final FileExtensionUtil fileExtensionUtil;
	private final SheetDetector sheetDetector;
	private final Config conf;

	/**
	 * 업로드된 파일을 분석하여 텍스트, 이미지, 시트 정보를 반환
	 */
	public TextInfoVO processText(final String filePath, final String fileName, final Options options, final boolean fileDelete) throws Exception {
		File file = new File(filePath);
		File imgDir = new File(options.getImagePath());
		try {
			FileExtensionUtil.Extension extension = resolveExtension(file, fileName);
			log.debug("EXTENSION | {}", extension);
			if (extension == null) return null;
			return buildTextInfo(file, extension, options, imgDir);
		} finally {
			if (fileDelete) {
				String dir = Common.makeFilepath(conf.getMemoryDiskPath(), options.getMsgId());
				cleanupResources(dir);
			}
		}
	}

	/**
	 * 파일 확장자 판별
	 */
	private FileExtensionUtil.Extension resolveExtension(File file, String fileName) throws Exception {
		String ext = textFilter.getExtension(file.getAbsolutePath());
		return fileExtensionUtil.getExtension(file, fileName, ext);
	}

	/**
	 * 파일 분석 결과(Text, Excel, 이미지 등)를 TextInfoVO로 생성
	 */
	private TextInfoVO buildTextInfo(File file, FileExtensionUtil.Extension extension, Options options, File imgDir) throws Exception {
		TextInfoVO textInfoVO = new TextInfoVO();
		textInfoVO.setEncrypted(extension.isEncrypted());
		textInfoVO.setDrm(extension.isDrm());
		textInfoVO.setExtension(extension.getExtension());
		textInfoVO.setChangeExtension(extension.isChangeType());
		textInfoVO.setUnknownType(extension.isUnknown());

		// DRM/암호화 파일은 상세 분석 불가
		if (extension.isDrm() || extension.isEncrypted()) {
			return textInfoVO;
		}

		// 텍스트 필터 실행
		TextFilterResult result = textFilter.filter(options, file.getAbsolutePath());
		textInfoVO.setText(result.getContent());

		// 문서내 OLE 객체 건수 탐지
		textInfoVO.setOleInfo(TextInfoVO.OLEInfo.builder().oleCount(result.getOLECount()).build());

		// Excel 히든 시트 탐지
		if (options.isCheckExcelHiddenSheet() && extension.isExcel()) {
			textInfoVO.setSheetInfo(sheetDetector.detect(file, extension.getExtension()));
		}

		if (options.isCheckArchiveImage()) {
			if (Arrays.stream(TextFilter.COMPRESS_EXT).anyMatch(e -> e.equals(extension.getExtension()))) {
				List<TextInfoVO.ArchiveInfo> archiveInfos = new ArrayList<>();
				List<CompressUtil.UnArchiveFile> files = CompressUtil.unArchiveFile(file.getAbsolutePath(), extension.getExtension(), options.getCheckArchiveDepth());
				for (CompressUtil.UnArchiveFile archiveFile : files) {
					FileExtensionUtil.Extension ext = resolveExtension(archiveFile.file(), archiveFile.name());
					if (TextFilter.IMAGE_EXTS.contains(FilenameUtils.getExtension(archiveFile.name())) || TextFilter.IMAGE_EXTS.contains(ext.getExtension())) {
						TextInfoVO.ArchiveInfo archiveInfo = new TextInfoVO.ArchiveInfo();
						archiveInfo.setImgPaths(archiveFile.path());
						archiveInfo.setImgNames(archiveFile.name());
						archiveInfo.setImgSizes(archiveFile.file().length());
						archiveInfo.setImgExts(ext);
						archiveInfo.setImgBase64s(Common.base64(archiveFile.file()));
						archiveInfos.add(archiveInfo);
					}
				}
				textInfoVO.setArchiveInfo(archiveInfos);
			}
		}

		// 문서내 이미지 추출
		if (options.isExtractImage()) {
			textInfoVO.setEmbeddedImage(convertImagesToBase64(imgDir));
			int imgCnt = textInfoVO.getEmbeddedImage().size() + (textInfoVO.getArchiveInfo() != null ? textInfoVO.getArchiveInfo().size() : 0);
			textInfoVO.setImagesCount(imgCnt);
		}
		return textInfoVO;
	}

	/**
	 * 이미지 디렉토리 내 파일을 Base64 문자열 리스트로 변환
	 */
	private List<TextInfoVO.EmbeddedImage> convertImagesToBase64(File imgDir) {
		List<TextInfoVO.EmbeddedImage> embeddedImages = new ArrayList<>();
		File[] images = imgDir.listFiles();
		if (images == null) {
			return embeddedImages;
		}
		for (File image : images) {
			try {
				embeddedImages.add(new TextInfoVO.EmbeddedImage(image.getName(), Common.base64(image)));
			} catch (Exception e) {
				log.warn("BASE64 | Image file to base64 error: {}", image.getAbsolutePath(), e);
			}
		}
		return embeddedImages;
	}

	/**
	 * 리소스 정리 (분석 후 파일/디렉토리 삭제)
	 */
	private void cleanupResources(String dir) {
		File directory = new File(dir);
		try {
			if (directory.exists()) FileUtils.deleteDirectory(directory);
		} catch (Exception e) {
			log.warn("REMOVE | Failed to delete directory: {}", directory.getAbsolutePath(), e);
		}
	}
}