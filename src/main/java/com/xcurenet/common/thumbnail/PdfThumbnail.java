package com.xcurenet.common.thumbnail;

import com.xcurenet.common.utils.Common;
import lombok.extern.log4j.Log4j2;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Log4j2
public class PdfThumbnail {

	/**
	 * PDF 첫 페이지를 이미지로 변환 후 썸네일 생성 (Base64 반환)
	 *
	 * @param path   입력 PDF 파일 Path
	 * @param width  썸네일 가로 크기
	 * @param height 썸네일 세로 크기
	 * @return Base64 인코딩된 JPEG 이미지 문자열 (변환 실패 시 null)
	 */
	public String execute(final Path path, final int width, final int height) {
		if (path == null || !Files.exists(path)) {
			log.warn("THUMBNAIL | FILE NOTFOUND : {}", (path != null ? path.toAbsolutePath() : null));
			return null;
		}

		try (InputStream in = Files.newInputStream(path);
		     PDDocument document = Loader.loadPDF(in.readAllBytes());
		     ByteArrayOutputStream out = new ByteArrayOutputStream()) {

			PDFRenderer pdfRenderer = new PDFRenderer(document);
			BufferedImage pageImage = pdfRenderer.renderImageWithDPI(0, 150, ImageType.RGB);
			Thumbnails.of(pageImage).forceSize(width, height).crop(Positions.CENTER).outputFormat("jpg").toOutputStream(out);
			return Common.toBase64(out.toByteArray());
		} catch (IOException e) {
			log.warn("THUMBNAIL | {} | {}", path.toAbsolutePath(), e.getMessage());
		}
		return null;
	}

	public static void main(String[] args) {
		PdfThumbnail pdfThumbnail = new PdfThumbnail();
		String out = pdfThumbnail.execute(Path.of("/users/tmp/sample.pdf"), 200, 200);
		log.info(out);
	}
}
