package com.xcurenet.common.utils;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Log4j2
public class FileUtil {

	public static String getText(final String path) {
		if (path == null) return null;
		try {
			return FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.warn("Failed to read file text: {}", path, e);
			return null;
		}
	}
}
