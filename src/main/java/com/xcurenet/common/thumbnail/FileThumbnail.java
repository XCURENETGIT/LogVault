package com.xcurenet.common.thumbnail;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FileThumbnail {
	private final ThumbnailRepository thumbnailRepository;
	private final int width = 100;
	private final int height = 100;
	private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif", "heic", "heif", "avif", "jxl", "raw", "cr2", "cr3", "nef", "nrw", "arw", "srf", "sr2", "orf", "rw2", "raf", "dng", "pef", "srw", "rwl", "3fr", "kdc", "mef", "mos", "x3f", "svg", "svgz", "ai", "eps", "cdr", "ico", "icns", "cur", "apng", "mng", "flif", "hdr", "exr", "pfm", "pgm", "ppm", "pbm", "pnm", "dicom", "dcm", "fits", "pcx", "tga", "sgi", "sun", "ras", "mac", "iff", "lbm", "pict", "pct", "fax");
	private static final Set<String> PDF_EXTENSIONS = Set.of("pdf");

	public boolean isExistThumbnail(final String hash) {
		return thumbnailRepository.isExistThumbnail(hash) > 0;
	}

	public void insertThumbnail(final String hash, final String base64) {
		thumbnailRepository.insertThumbnail(hash, base64);
	}

	public String execute(final String ext, final File file, final String text) {
		final String lowerExt = ext == null ? "" : ext.toLowerCase();
		if (IMAGE_EXTENSIONS.contains(lowerExt)) {
			return generateImage(file);
		} else if (PDF_EXTENSIONS.contains(lowerExt)) {
			return generatePdf(file);
		} else {
			if (text == null) return null;
			return generateText(text);
		}
	}

	private String generateText(final String text) {
		return new TextThumbnail().execute(text, width, height);
	}

	private String generatePdf(final File file) {
		return new PdfThumbnail().execute(file, width, height);
	}

	private String generateImage(final File file) {
		return new ImageThumbnail().execute(file, width, height);
	}
}
