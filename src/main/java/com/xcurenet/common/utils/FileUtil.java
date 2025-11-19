package com.xcurenet.common.utils;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
				return Files.readString(file, StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			log.warn("Failed to read file text: {}", path, e);
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
