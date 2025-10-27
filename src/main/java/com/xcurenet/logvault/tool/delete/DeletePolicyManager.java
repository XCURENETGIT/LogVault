package com.xcurenet.logvault.tool.delete;

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
import java.util.Comparator;
import java.util.stream.Stream;

@Log4j2
@Component
@RequiredArgsConstructor
public class DeletePolicyManager {

	private final Config conf;
	private final FileProcessor fileProcessor;
	private final FileCleanupService cleanupService;

	@Scheduled(cron = "0 0 2 * * *")
	private void cleanupExpiredData() {
		if (Common.isWindow()) return;

		log.info("[RM_EXPIRE] {} | {}", conf.getDataStoreTerm(), conf.getAttachRoot());
		if (isInvalidPath(conf.getAttachRoot())) return;

		cleanupService.runCleanup(conf.getDataStoreTerm());
	}

	@Scheduled(cron = "0 0 * * * *")
	private void cleanupByStorageLimit() throws IOException {
		if (Common.isWindow()) return;
		execute(conf.getAttachRoot());
		execute(conf.getIndexRoot());
	}

	private void execute(final String path) throws IOException {
		if (isInvalidPath(path) || Common.isEquals(conf.getDataStoreUsage(), "N")) return;

		double usageThreshold = ((double) Common.nvz(conf.getDataStoreUsageLimit(), 90) / 100.0); // 예: 0.80
		long totalSpace = fileProcessor.getTotalSpace(path);
		long usableSpace = fileProcessor.getUsableSpace(path);
		long usedSpace = totalSpace - usableSpace;
		double usageRatio = (double) usedSpace / (double) totalSpace;

		currentDiskLog(path, usageThreshold, "START");

		if (usageRatio >= usageThreshold) {
			cleanupService.runCleanup(path, usageThreshold);
			currentDiskLog(path, usageThreshold, "END");
			deleteDirectory(path);
		}
	}

	private boolean isInvalidPath(final String path) {
		if (Common.isEmpty(path)) return true;
		return !fileProcessor.exists(path);
	}

	private void currentDiskLog(final String path, final double usageThreshold, final String prefix) {
		long totalSpace = fileProcessor.getTotalSpace(path);
		long usableSpace = fileProcessor.getUsableSpace(path);
		long usedSpace = totalSpace - usableSpace;   // used space
		double usageRatio = (double) usedSpace / totalSpace;

		String totalStr = Common.convertFileSize(totalSpace);
		String usedStr = Common.convertFileSize(usedSpace);
		String usagePercent = String.format("%.2f", usageRatio * 100);
		log.info("[DISK_INFO] {} Path:{} | Total:{} | Used:{} | Usage:{}% | Threshold:{}{}%", prefix, path, totalStr, usedStr, usagePercent, String.format("%.2f", usageThreshold * 100), prefix.equals("START") ? "" : "\n");
	}

	private void deleteDirectory(final String rootPath) throws IOException {
		Path root = Path.of(rootPath);
		try (Stream<Path> paths = Files.walk(root)) {
			paths.sorted(Comparator.reverseOrder()).filter(Files::isDirectory).forEach(dir -> {
				if (dir.equals(root)) return;
				try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
					if (!ds.iterator().hasNext()) {
						Files.delete(dir);
						log.info("Deleted empty dir: {}", dir);
					}
				} catch (Exception e) {
					log.warn("Error deleting: {} {}", dir, e.getMessage());
				}
			});
		}
	}
}
