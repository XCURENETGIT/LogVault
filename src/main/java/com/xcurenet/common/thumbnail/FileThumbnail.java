package com.xcurenet.common.thumbnail;

import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.nio.file.Path;
import java.util.Set;

@Log4j2
@Service
@RequiredArgsConstructor
public class FileThumbnail {
	private final Config conf;
	private final ThumbnailRepository thumbnailRepository;
	private final int width = 100;
	private final int height = 100;
	private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif", "heic", "heif", "avif", "jxl", "raw", "cr2", "cr3", "nef", "nrw", "arw", "srf", "sr2", "orf", "rw2", "raf", "dng", "pef", "srw", "rwl", "3fr", "kdc", "mef", "mos", "x3f", "svg", "svgz", "ai", "eps", "cdr", "ico", "icns", "cur", "apng", "mng", "flif", "hdr", "exr", "pfm", "pgm", "ppm", "pbm", "pnm", "dicom", "dcm", "fits", "pcx", "tga", "sgi", "sun", "ras", "mac", "iff", "lbm", "pict", "pct", "fax");
	private static final Set<String> PDF_EXTENSIONS = Set.of("pdf");

	@Scheduled(cron = "0 0 3 * * *")
	private void cleanup() {
		if (Common.isWindow()) return;

		StopWatch sw = DateUtils.start();
		thumbnailRepository.deleteThumbnail(conf.getThumbnailRetentionDays());
		log.info("RM_THUMBNAIL | {} | {}", conf.getThumbnailRetentionDays(), DateUtils.stop(sw));
	}

	public boolean isExistThumbnail(final String hash) {
		return thumbnailRepository.isExistThumbnail(hash) > 0;
	}

	public void insertThumbnail(final String hash, final String base64) {
		thumbnailRepository.insertThumbnail(hash, base64);
	}

	/**
	 * 파일 썸네일 생성 (Path 기반)
	 *
	 * @param ext  파일 확장자 (예: "pdf", "jpg"...)
	 * @param path 파일 Path
	 * @param text 텍스트 썸네일용 내용
	 * @return base64 썸네일 문자열, 실패/대상 아님이면 null
	 */
	public String execute(final String ext, final Path path, final String text) {
		final String lowerExt = ext == null ? "" : ext.toLowerCase();

		if (IMAGE_EXTENSIONS.contains(lowerExt)) {
			return generateImage(path);
		} else if (PDF_EXTENSIONS.contains(lowerExt)) {
			return generatePdf(path);
		} else {
			if (text == null) return null;
			return generateText(text);
		}
	}

	private String generateText(final String text) {
		return new TextThumbnail().execute(text, width, height);
	}

	private String generatePdf(final Path path) {
		return new PdfThumbnail().execute(path, width, height);
	}

	private String generateImage(final Path path) {
		return new ImageThumbnail().execute(path, width, height);
	}
}
