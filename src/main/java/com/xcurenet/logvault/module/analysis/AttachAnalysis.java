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

	/* =========================
	 * Image Directory
	 * ========================= */
	private String imageDir(Options options) {
		String imgPath = null;
		try {
			imgPath = Common.makeFilepath(conf.getMemoryDiskPath(), options.getMsgId(), TextFilter.IMG_DIR);
			FileUtils.forceMkdir(new File(Objects.requireNonNull(imgPath)));
		} catch (Exception e) {
			log.warn("{} | {} | msgId={} | {}", ErrorCode.ATTACH_MKDIR_FAIL, ErrorCode.fromCode(ErrorCode.ATTACH_MKDIR_FAIL), options.getMsgId(), e.getMessage(), e);
		}
		return imgPath;
	}

	/* =========================
	 * Text Extract
	 * ========================= */
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
			log.warn("{} | {} | path={} | name={} | {}", ErrorCode.ATTACH_TEXT_EXTRACT_FAIL, ErrorCode.fromCode(ErrorCode.ATTACH_TEXT_EXTRACT_FAIL), filePath, fileName, e.getMessage(), e);
		}
		return null;
	}

	/* =========================
	 * Attach Text
	 * ========================= */
	public void setAttachText(final ScanData msg) {
		if (msg == null || msg.getEmassDoc() == null) {
			log.warn("{} | {}", ErrorCode.ATTACH_MSGDATA_NULL, ErrorCode.fromCode(ErrorCode.ATTACH_MSGDATA_NULL));
			return;
		}

		EmassDoc doc = msg.getEmassDoc();
		List<EmassDoc.Attach> attaches = doc.getAttach();
		if (attaches == null) {
			log.warn("{} | {} | msgId={}", ErrorCode.ATTACH_LIST_NULL, ErrorCode.fromCode(ErrorCode.ATTACH_LIST_NULL), doc.getMsgid());
			return;
		}

		for (EmassDoc.Attach attach : attaches) {
			attach.setOcrTarget(false);
			attach.setExpectedUnknown(true);
			attach.setChangeExtension(false);
			attach.setEncrypted(false);

			if (!attach.isExist()) {
				log.warn("{} | {} | path={}", ErrorCode.ATTACH_FILE_NOT_EXIST, ErrorCode.fromCode(ErrorCode.ATTACH_FILE_NOT_EXIST), attach.getSrcPath());
				continue;
			}

			if (isFileOverSize(attach.getSrcPath(), conf.getFileAnalysisLimitSize())) {
				continue;
			}

			StopWatch sw = DateUtils.start();
			TextInfoVO data = getText(doc.getMsgid(), attach.getSrcPath(), attach.getName());

			if (data != null) {
				String limitText = Common.limitLength(data.getText(), conf.getTextLimitLength());
				limitText = Common.limitTokenLengthWithSpace(limitText, conf.getTextLimitToken());

				attach.setText(limitText);
				attach.setExpectedExtension(data.getExtension());
				attach.setExpectedUnknown(data.isUnknownType());
				attach.setChangeExtension(data.isChangeExtension());
				attach.setEncrypted(data.isEncrypted());

				setExcelHiddenSheet(attach, data);
				setEmbeddedImage(doc, msg.getMsgData(), attach, data);
				setOCRTarget(doc, attach);

				log.info("ATT_TEXT | {} | TXT_LEN:{} | {}", conf.getDataPathSmall(attach.getSrcPath()), Common.nvl(attach.getText()).length(), DateUtils.stop(sw));
			} else {
				log.warn("{} | {} | path={}", ErrorCode.ATTACH_TEXT_EXTRACT_FAIL, ErrorCode.fromCode(ErrorCode.ATTACH_TEXT_EXTRACT_FAIL), conf.getDataPathSmall(attach.getSrcPath()));
			}
		}
	}

	/* =========================
	 * Embedded Image
	 * ========================= */
	private void setEmbeddedImage(EmassDoc doc, MSGData msgData, EmassDoc.Attach attach, TextInfoVO data) {
		try {
			if (data.getEmbeddedImage() != null && data.getImagesCount() > 0) {
				List<String> embeddedFiles = msgData.getEmbeddedFile();
				List<EmassDoc.ImageExtractorInfo> infos = new ArrayList<>();

				for (TextInfoVO.EmbeddedImage embedded : data.getEmbeddedImage()) {
					if (embedded.getBase64() == null) continue;

					String ext = FilenameUtils.getExtension(embedded.getName());
					String name = Common.makeMD5Hex(Paths.get(attach.getSrcPath()).getFileName().toString(), embedded.getName()) + "." + ext;

					String srcPath = Common.makeFilepath(Paths.get(attach.getSrcPath()).getParent().toString(), name);
					String dest = conf.getDestPath(msgData.getCtime(), msgData.getMsgid(), name);

					Files.write(Path.of(Objects.requireNonNull(srcPath)), Common.decodeBase64(embedded.getBase64()), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

					embeddedFiles.add(srcPath);
					infos.add(EmassDoc.ImageExtractorInfo.builder().name(embedded.getName()).path(dest).build());
				}

				msgData.setEmbeddedFile(embeddedFiles);
				attach.setImageExtractorInfo(infos);

				if (conf.isOcrApiEnable() && conf.isOcrEmbeddedImageEnable()) {
					doc.getProcessStatus().setOcr("P");
					attach.setOcrStatus("P");
					attach.setOcrTarget(true);
				}
			}
		} catch (Exception e) {
			log.warn("{} | {} | path={} | {}", ErrorCode.ATTACH_IMAGE_EXTRACT_FAIL, ErrorCode.fromCode(ErrorCode.ATTACH_IMAGE_EXTRACT_FAIL), conf.getDataPathSmall(attach.getSrcPath()), e.getMessage(), e);
		}
	}

	/* =========================
	 * OCR Target
	 * ========================= */
	private void setOCRTarget(EmassDoc doc, EmassDoc.Attach attach) {
		if (!conf.isOcrApiEnable()) return;

		String ext = Common.nvl(attach.getExtension());
		if (!isFileOverSize(attach.getSrcPath(), conf.getOcrLimitSize()) && (conf.getOcrTargetExt().contains(attach.getExpectedExtension()) || conf.getOcrTargetExt().contains(ext))) {

			doc.getProcessStatus().setOcr("P");
			attach.setOcrStatus("P");
			attach.setOcrTarget(true);
		}
	}

	/* =========================
	 * Excel Hidden Sheet
	 * ========================= */
	private void setExcelHiddenSheet(EmassDoc.Attach attach, TextInfoVO data) {
		try {
			if (data.getSheetInfo() != null) {
				SheetDetector.SheetInfo sheet = data.getSheetInfo();
				EmassDoc.SheetInfo info = new EmassDoc.SheetInfo();
				info.setSheetTotal(sheet.getSheetTotal());
				info.setSheetHiddenTotal(sheet.getSheetHiddenTotal());
				info.setHiddenSheetNames(sheet.getHiddenSheetNames());
				attach.setSheetInfo(info);
			}
		} catch (Exception e) {
			log.warn("{} | {} | path={} | {}", ErrorCode.ATTACH_SHEET_INFO_FAIL, ErrorCode.fromCode(ErrorCode.ATTACH_SHEET_INFO_FAIL), conf.getDataPathSmall(attach.getSrcPath()), e.getMessage(), e);
		}
	}

	/* =========================
	 * File Size Check
	 * ========================= */
	private boolean isFileOverSize(final String filePath, final long limitSize) {
		try {
			Path path = Paths.get(filePath);
			return Files.size(path) > limitSize;
		} catch (IOException e) {
			log.warn("{} | {} | path={} err={}", ErrorCode.FILE_ANALYSIS_SIZE, ErrorCode.fromCode(ErrorCode.FILE_ANALYSIS_SIZE), filePath, e.toString());
			return true;
		}
	}

	public void setAttachThumbnail(final ScanData msg) {
		if (msg == null || msg.getEmassDoc() == null) {
			log.warn("{} | {}", ErrorCode.ATTACH_MSGDATA_NULL, ErrorCode.fromCode(ErrorCode.ATTACH_MSGDATA_NULL));
			return;
		}

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
						log.info("THUMBNAIL | {} | {}", conf.getDataPathSmall(attach.getSrcPath()), DateUtils.stop(sw));
					}
				}
			}
		} catch (Exception e) {
			log.warn("{} | {} | {}", ErrorCode.ATTACH_THUMBNAIL_FAIL, ErrorCode.fromCode(ErrorCode.ATTACH_THUMBNAIL_FAIL), e.getMessage(), e);
		}
	}

	public static void main(String[] args) {
		StopWatch sw = new StopWatch();
		sw.start();
		for (int i = 0; i < 100; i++) {
			Common.sleep(1);
		}
		sw.stop();
		String formatted = DurationFormatUtils.formatDuration(sw.getTotalTimeMillis(), "s.SSS's'");
		System.out.println(formatted);
	}
}
