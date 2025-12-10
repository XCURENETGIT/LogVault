package com.xcurenet.common.fileanalysis.service.extension;

import lombok.extern.log4j.Log4j2;
import org.apache.tika.mime.MimeTypes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Log4j2
@Component
public class FileCommand {

	@Value("${edc.extension.file.command:/usr/bin/file}")
	private String fileCommand;

	private static final List<String> OPTION = Arrays.asList("-b", "--mime-type");

	public String getExtension(final File file) {
		List<String> cmd = new ArrayList<>(getCommand(file.getAbsolutePath()));

		try {
			String ext = processFirstLine(cmd);
			String fileExt = MimeTypes.getDefaultMimeTypes().forName(ext).getExtension();
			if (fileExt != null && !fileExt.isEmpty()) {
				if (".bin".equals(fileExt)) fileExt = FileExtensionUtil.UNKNOWN;
				else fileExt = fileExt.substring(1);
			} else {
				fileExt = FileExtensionUtil.UNKNOWN;
			}
			return fileExt;
		} catch (Exception e) {
			log.warn("FILE_CMD | {} | {}", cmd, e.getMessage());
		}
		return FileExtensionUtil.UNKNOWN;
	}

	private List<String> getCommand(String filePath) {
		List<String> command = new ArrayList<>();
		command.add(fileCommand);
		command.addAll(OPTION);
		command.add(filePath);
		return command;
	}

	private String processFirstLine(final List<String> command) throws IOException {
		if (command == null || command.isEmpty()) return null;

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);
		Process process = pb.start();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			return reader.readLine();
		} finally {
			process.destroy();
		}
	}

}
