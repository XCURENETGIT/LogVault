package com.xcurenet.common.thumbnail;

import com.xcurenet.common.utils.Common;
import lombok.extern.log4j.Log4j2;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Log4j2
public class ImageThumbnail {

	/**
	 * 이미지의 썸네일 변환 후 Base64 반환
	 *
	 * @param path   입력 이미지 파일 Path
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
		     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Thumbnails.of(in).forceSize(width, height).crop(Positions.CENTER).outputFormat("jpg").toOutputStream(out);
			return Common.toBase64(out.toByteArray());
		} catch (IOException e) {
			log.warn("THUMBNAIL | {} | {}", path.toAbsolutePath(), e.getMessage());
		}
		return null;
	}

	public static void main(String[] args) {
		ImageThumbnail imageThumbnail = new ImageThumbnail();
		String out = imageThumbnail.execute(Path.of("/users/tmp/test_img1.jpg"), 200, 200);
		log.info(out);
	}
}
