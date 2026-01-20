package com.xcurenet.logvault.job.delete;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.fs.FileProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Log4j2
@Component
@RequiredArgsConstructor
public class NokPolicyManager {
	private final Config conf;
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final Pattern DATE_DIR_PATTERN = Pattern.compile("\\d{8}");

	@Scheduled(cron = "0 0 2 * * *")
	private void cleanupExpiredData() {
		if (Common.isWindow()) return;

		log.info("RM_NOK_EXPIRE | {} | {}", conf.getNokRetentionDays(), conf.getNokRoot());
		if (conf.getNokRetentionDays() <= 0) {
			log.warn("RM_NOK_EXPIRE_SKIP | invalid retentionDays={}", conf.getNokRetentionDays());
			return;
		}
		if (isInvalidPath(conf.getNokRoot())) return;

		execute();
	}

	private void execute() {
		Path rootPath = Path.of(conf.getNokRoot());
		LocalDate today = LocalDate.now();
		try (DirectoryStream<Path> dirs = Files.newDirectoryStream(rootPath)) {
			for (Path dir : dirs) {
				if (!Files.isDirectory(dir)) continue;
				if (Files.isSymbolicLink(dir)) continue;

				String name = dir.getFileName().toString();
				if (!DATE_DIR_PATTERN.matcher(name).matches()) {
					log.debug("RM_NOK_SKIP_NOT_DATE_DIR | {}", dir);
					continue;
				}

				LocalDate dirDate;
				try {
					dirDate = LocalDate.parse(name, DATE_FMT);
				} catch (Exception e) {
					log.warn("RM_NOK_SKIP_PARSE_FAIL | {}", dir);
					continue;
				}

				long age = ChronoUnit.DAYS.between(dirDate, today);
				if (age < conf.getNokRetentionDays()) continue;
				if (age < 0) continue;

				deleteDirectory(dir);
			}
		} catch (Exception e) {
			log.error("RM_NOK_SCAN_FAIL | root: {}", conf.getNokRoot(), e);
		}
	}

	private void deleteDirectory(Path dir) {
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					log.error("RM_NOK_DELETE_FAIL | {}", p, e);
				}
			});
			log.info("RM_NOK_DELETE_OK | {}", dir);
		} catch (Exception e) {
			log.error("RM_NOK_DELETE_DIR_FAIL | {}", dir, e);
		}
	}

	private boolean isInvalidPath(final String path) {
		if (Common.isEmpty(path)) return true;
		Path p = Path.of(path);
		return !Files.isDirectory(p);
	}
}
