package com.xcurenet.common.thumbnail;

import com.xcurenet.common.utils.CommonUtil;
import lombok.extern.log4j.Log4j2;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

@Log4j2
public class PdfThumbnail {

	/**
	 * PDF 첫 페이지를 이미지로 변환 후 썸네일 생성 (Base64 반환)
	 *
	 * @param file   입력 PDF 파일
	 * @param width  썸네일 가로 크기
	 * @param height 썸네일 세로 크기
	 * @return Base64 인코딩된 JPEG 이미지 문자열 (변환 실패 시 null)
	 */
	public String execute(final File file, final int width, final int height) {
		if (file == null || !file.exists()) {
			log.warn("[THUMNAIL] FILE NOTFOUND : {}", (file != null ? file.getAbsolutePath() : null));
			return null;
		}

		try (PDDocument document = Loader.loadPDF(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDFRenderer pdfRenderer = new PDFRenderer(document);
			BufferedImage pageImage = pdfRenderer.renderImageWithDPI(0, 150, ImageType.RGB);
			Thumbnails.of(pageImage).forceSize(width, height).crop(Positions.CENTER).outputFormat("jpg").toOutputStream(out);
			return CommonUtil.toBase64(out.toByteArray());
		} catch (IOException e) {
			log.warn("[THUMNAIL] {} | {}", file.getPath(), e.getMessage());
		}
		return null;
	}

	public static void main(String[] args) {
		PdfThumbnail pdfThumbnail = new PdfThumbnail();
		String out = pdfThumbnail.execute(new File("/users/tmp/sample.pdf"), 200, 200);
		log.info(out);
	}
}
