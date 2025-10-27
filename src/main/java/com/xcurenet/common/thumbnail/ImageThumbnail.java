package com.xcurenet.common.thumbnail;

import com.xcurenet.common.utils.Common;
import lombok.extern.log4j.Log4j2;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Log4j2
public class ImageThumbnail {

	/**
	 * 이미지의 썸네일 변환 후  Base64 반환
	 *
	 * @param file   입력 이미지 파일
	 * @param width  썸네일 가로 크기
	 * @param height 썸네일 세로 크기
	 * @return Base64 인코딩된 JPEG 이미지 문자열 (변환 실패 시 null)
	 */
	public String execute(final File file, final int width, final int height) {
		if (file == null || !file.exists()) {
			log.warn("[THUMNAIL] FILE NOTFOUND : {}", (file != null ? file.getAbsolutePath() : null));
			return null;
		}

		try (ByteArrayOutputStream out = new ByteArrayOutputStream(); FileInputStream fis = new FileInputStream(file);) {
			Thumbnails.of(fis).forceSize(width, height).crop(Positions.CENTER).outputFormat("jpg").toOutputStream(out);
			return Common.toBase64(out.toByteArray());
		} catch (IOException e) {
			log.warn("[THUMNAIL] {} | {}", file.getPath(), e.getMessage());
		}
		return null;
	}

	public static void main(String[] args) {
		ImageThumbnail imageThumbnail = new ImageThumbnail();
		String out = imageThumbnail.execute(new File("/users/tmp/test_img1.jpg"), 200, 200);
		log.info(out);
	}
}
