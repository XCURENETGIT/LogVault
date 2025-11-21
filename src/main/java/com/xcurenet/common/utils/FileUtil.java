package com.xcurenet.common.utils;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Log4j2
public class FileUtil {

	public static String getText(final String path) {
		if (path == null) return null;

		try {
			Path file = Paths.get(path);
			if (Files.isRegularFile(file) && Files.isReadable(file)) {
				byte[] fileBytes = Files.readAllBytes(file);
				Charset charset = detectCharset(fileBytes);
				if (charset != null) {
					return new String(fileBytes, charset);
				}
				try {
					CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
					decoder.onMalformedInput(CodingErrorAction.REPORT);
					decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
					return decoder.decode(ByteBuffer.wrap(fileBytes)).toString();
				} catch (CharacterCodingException e) {
					return new String(fileBytes, "MS949");
				}
			}
		} catch (IOException e) {
			log.warn("Failed to read file: {}", path, e);
		}
		return null;
	}

	/**
	 * 바이트 배열의 헤더(BOM)를 확인하여 Charset 반환
	 */
	private static Charset detectCharset(byte[] bytes) {
		if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
			return StandardCharsets.UTF_8; // UTF-8 BOM
		} else if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
			return StandardCharsets.UTF_16BE; // UTF-16 Big Endian
		} else if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
			return StandardCharsets.UTF_16LE; // UTF-16 Little Endian (윈도우 기본)
		}
		return null;
	}

	public static String getExtension(final String name) {
		try {
			return FilenameUtils.getExtension(name);
		} catch (Exception e) {
			return null;
		}
	}
}
