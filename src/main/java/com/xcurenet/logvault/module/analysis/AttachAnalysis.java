package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.fileanalysis.service.FileService;
import com.xcurenet.common.fileanalysis.service.TextInfoVO;
import com.xcurenet.common.fileanalysis.service.extension.excel.SheetDetector;
import com.xcurenet.common.fileanalysis.service.option.Options;
import com.xcurenet.common.fileanalysis.service.option.PathOptions;
import com.xcurenet.common.fileanalysis.service.text.TextFilter;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.thumbnail.FileThumbnail;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Log4j2
@Service
@RequiredArgsConstructor
public class AttachAnalysis {
	private final Config conf;
	private final FileThumbnail fileThumbnail;
	private final FileService fileService;

	private String imageDir(Options options) {
		String imgPath = null;
		try {
			imgPath = Common.makeFilepath(conf.getMemoryDiskPath(), options.getMsgId(), TextFilter.IMG_DIR);
			FileUtils.forceMkdir(new File(Objects.requireNonNull(imgPath)));
		} catch (Exception e) {
			log.warn("[MKDIR] ", e);
		}
		return imgPath;
	}

	private TextInfoVO getText(final String msgId, final String filePath, final String fileName) {
		PathOptions options = new PathOptions();
		options.setMsgId(msgId);
		options.setFilePath(filePath);
		options.setFileName(fileName);
		options.setImagePath(imageDir(options));
		options.setExtractImage(conf.isExtractImage());
		options.setCheckArchiveImage(conf.isExtractImage());
		options.setCheckArchiveDepth(conf.getDecompressDepth());
		options.setCheckExcelHiddenSheet(conf.isCheckExcelHiddenSheet());

		try {
			return fileService.processText(options.getFilePath(), options.getFileName(), options, false);
		} catch (Exception e) {
			log.warn("GET_TEXT | {}", filePath, e);
		}
		return null;
	}

	public void setAttachText(final ScanData msg) {
		EmassDoc doc = msg.getEmassDoc();
		List<EmassDoc.Attach> attaches = doc.getAttach();
		if (attaches == null) return;

		for (EmassDoc.Attach attach : attaches) {
			attach.setOcrTarget(false);
			attach.setExpectedUnknown(true);
			attach.setChangeExtension(false);
			attach.setEncrypted(false);

			// 설정된 사이즈보다 큰 파일의 경우 텍스트 추출을 하지 않는다.
			if (!attach.isExist() || isFileOverSize(attach.getSrcPath(), conf.getFileAnalysisLimitSize())) {
				continue;
			}

			StopWatch sw = DateUtils.start();
			TextInfoVO data = getText(doc.getMsgid(), attach.getSrcPath(), attach.getName());
			if (data != null) {
				String limitText = Common.limitLength(data.getText(), conf.getTextLimitLength());
				limitText = Common.limitTokenLengthWithSpace(limitText, conf.getTextLimitToken()); // 텍스트 추출 후 최대 사이즈 제한까지 등록

				attach.setText(limitText);
				attach.setExpectedExtension(data.getExtension());       // 예상 확장자
				attach.setExpectedUnknown(data.isUnknownType());      // 알수없는 확장자
				attach.setChangeExtension(data.isChangeExtension());  // 확장자 변경 유무
				attach.setEncrypted(data.isEncrypted());              // 암호화 유무
				setExcelHiddenSheet(attach, data);                    // 엑셀 히드시트 정보 추가

				setEmbeddedImage(doc, msg.getMsgData(), attach, data); // 파일내 이미지 추출 정보
				setOCRTarget(doc, attach);                             // OCR 대상 설정

				log.info("ATT_TEXT | {} | TXT_LEN:{} | {}", conf.getDataPathSmall(attach.getSrcPath()), Common.nvl(attach.getText()).length(), DateUtils.stop(sw));
			} else {
				log.warn("ATT_TEXT | {} | TXT_LEN:{} | {}", conf.getDataPathSmall(attach.getSrcPath()), Common.nvl(attach.getText()).length(), DateUtils.stop(sw));
			}
		}
	}

	// 파일내 이미지 추출 정보
	private void setEmbeddedImage(EmassDoc doc, MSGData msgData, EmassDoc.Attach attach, TextInfoVO data) {
		try {
			if (data.getEmbeddedImage() != null && data.getImagesCount() > 0) {
				List<TextInfoVO.EmbeddedImage> array = data.getEmbeddedImage();
				List<String> embeddedFiles = msgData.getEmbeddedFile();
				List<EmassDoc.ImageExtractorInfo> imageExtractorInfos = new ArrayList<>();
				for (TextInfoVO.EmbeddedImage embedded : array) {
					if (embedded.getBase64() == null) continue;

					String ext = FilenameUtils.getExtension(embedded.getName());
					String name = Common.makeMD5Hex(Paths.get(attach.getSrcPath()).getFileName().toString(), embedded.getName()) + "." + ext; //첨부이름 + 이미지 이름 MD5
					String srcPath = Common.makeFilepath(Paths.get(attach.getSrcPath()).getParent().toString(), name); //임시 SRC 저장 위치
					String dest = conf.getDestPath(msgData.getCtime(), msgData.getMsgid(), name); //최종 저장 위치
					Files.write(Path.of(Objects.requireNonNull(srcPath)), Common.decodeBase64(embedded.getBase64()), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

					embeddedFiles.add(srcPath);

					log.info("OLE__IMG | {} | {}", embedded.getName(), Common.getBase64Size(embedded.getBase64()));
					imageExtractorInfos.add(EmassDoc.ImageExtractorInfo.builder().name(embedded.getName()).path(dest).build());
				}
				msgData.setEmbeddedFile(embeddedFiles); //파일 전송을 위한 저장
				attach.setImageExtractorInfo(imageExtractorInfos);

				if (conf.isOcrApiEnable() && conf.isOcrEmbeddedImageEnable()) {
					doc.getProcessStatus().setOcr("P");
					attach.setOcrStatus("P"); // PENDING, 파일 내부에 있는 이미지도 OCR 대상임
					attach.setOcrTarget(true);
				}
			}
		} catch (Exception e) {
			log.warn("ATT_OLE_IMG | {} | {}", conf.getDataPathSmall(attach.getSrcPath()), e.getMessage());
		}
	}

	// OCR 대상 여부
	private void setOCRTarget(EmassDoc doc, EmassDoc.Attach attach) {
		if (!conf.isOcrApiEnable()) return;

		// OCR 사이즈 제한보다 작아야 되며, 예상확장자 혹은 파일의 확장자가 이미지 타입인 경우 OCR 대상임.
		String ext = Common.nvl(attach.getExtension());
		if (!isFileOverSize(attach.getSrcPath(), conf.getOcrLimitSize()) && (conf.getOcrTargetExt().contains(attach.getExpectedExtension()) || conf.getOcrTargetExt().contains(ext))) {
			doc.getProcessStatus().setOcr("P");
			attach.setOcrStatus("P"); // PENDING
			attach.setOcrTarget(true);
		}
	}

	// 엑셀 Hidden Sheet 정보
	private void setExcelHiddenSheet(EmassDoc.Attach attach, TextInfoVO data) {
		try {
			if (data.getSheetInfo() != null) {
				SheetDetector.SheetInfo sheet = data.getSheetInfo();
				EmassDoc.SheetInfo sheetInfo = new EmassDoc.SheetInfo();
				sheetInfo.setSheetTotal(sheet.getSheetTotal());
				sheetInfo.setSheetHiddenTotal(sheet.getSheetHiddenTotal());
				sheetInfo.setHiddenSheetNames(sheet.getHiddenSheetNames());
				attach.setSheetInfo(sheetInfo);
			}
		} catch (Exception e) {
			log.warn("ATT_SHEET | {} | {}", conf.getDataPathSmall(attach.getSrcPath()), e.getMessage());
		}
	}

	private boolean isFileOverSize(final String filePath, final long limitSize) {
		try {
			Path path = Paths.get(filePath);
			if (Files.size(path) > limitSize) return true;
		} catch (IOException e) {
			log.warn("{} | {} | path:{} err={}", ErrorCode.FILE_ANALYSIS_SIZE, ErrorCode.fromCode(ErrorCode.FILE_ANALYSIS_SIZE), filePath, e.toString());
			return true;
		}
		return false;
	}

	public void setAttachThumbnail(final ScanData msg) {
		EmassDoc doc = msg.getEmassDoc();
		try {
			List<EmassDoc.Attach> attaches = doc.getAttach();
			if (attaches == null) return;

			for (EmassDoc.Attach attach : attaches) {
				if (!attach.isExist()) continue;

				Path path = Paths.get(attach.getSrcPath());
				if (!Files.exists(path)) continue;

				if (!fileThumbnail.isExistThumbnail(attach.getHash())) {
					StopWatch sw = DateUtils.start();
					String thumbnail = fileThumbnail.execute(attach.getExpectedExtension(), path, attach.getText());
					if (thumbnail != null) {
						fileThumbnail.insertThumbnail(attach.getHash(), thumbnail);
						log.info("THUMNAIL | {} | {}", conf.getDataPathSmall(attach.getSrcPath()), DateUtils.stop(sw));
					}
				}
			}
		} catch (Exception e) {
			log.warn("THUMNAIL | {}", e.getMessage());
		}
	}

	public static void main(String[] args) {
		StopWatch sw = new StopWatch();
		sw.start();
		for (int i = 1; i <= 100; i++) {
			Common.sleep(1);
		}
		sw.stop();
		String formatted = DurationFormatUtils.formatDuration(sw.getTotalTimeMillis(), "s.SSS's'");
		System.out.println(formatted);
	}

//
//	private JSONObject getText(final String msgId, final String filePath, final String fileName) {
//		LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
//		body.add("msgId", msgId);
//		body.add("filePath", filePath);
//		body.add("fileName", fileName);
//		body.add("extractImage", conf.isExtractImage());
//		body.add("checkArchiveImage", conf.isExtractImage());
//		body.add("checkArchiveDepth", conf.getDecompressDepth());
//		body.add("checkExcelHiddenSheet", conf.isCheckExcelHiddenSheet());
//
//		int maxRetries = 3;
//		int attempt = 0;
//		while (attempt < maxRetries) {
//			try {
//				attempt++;
//				return restClient.post().uri(conf.getFileAnalysisUrl()).contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(JSONObject.class);
//			} catch (Exception e) {
//				log.warn("GET_TEXT | {} | ({}/{}) | {}", filePath, attempt, maxRetries, e.getMessage());
//				if (attempt < maxRetries) Common.sleep(1000);
//			}
//		}
//		return null;
//	}
}
