package com.xcurenet.logvault.job.delete;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.fs.FileProcessor;
import com.xcurenet.logvault.module.task.service.TaskMessageRepository;
import com.xcurenet.logvault.opensearch.IndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class FileCleanupService {
	// OpenSearch 삭제 시 오래된 data의 경우 readonly를 풀어야 하는지 확인???
	// 지워야할 파티션, /data01, /indexdata index의 경우 1일 index를 삭제하지 않으면 공간을 확보하기 어려움. (즉, 색인(index) 지워야 용량 확보 가능함.)
	// doc단위 삭제가 아닌, 일단위 삭제가 유일한 해답이 될것으로 보임.
	private static final String DELETE_TYPE_RETENTION = "RETENTION";
	private static final String DELETE_TYPE_CAPACITY = "CAPACITY";

	private final IndexService indexService;
	private final FileProcessor fileProcessor;
	private final TaskMessageRepository repository;
	private final Config conf;

	public void runCleanupRetention(final int term) {
		List<Map<String, String>> indices = getIndicesOrNull();
		if (indices == null || indices.isEmpty()) {
			log.info("END_EMPTY | No more index to delete.");
			return;
		}

		DateTime today = DateTime.now();
		for (Map<String, String> item : indices) {
			String date = Common.nvl(item.get("date"));
			if (isRetentionTarget(date, term, today)) {
				delete(date, Common.nvl(item.get("index")), DELETE_TYPE_RETENTION);
			}
		}
	}

	private void delete(final String date, final String index, final String deleteType) {
		String attachPath = Common.makeFilepath(conf.getAttachRoot(), date);
		if (!deleteAttachDirectory(attachPath)) {
			return;
		}
		deleteIndexAndSaveHistory(date, index, deleteType);
	}

	private boolean deleteAttachDirectory(final String attachPath) {
		try {
			boolean attachDeleted = fileProcessor.deleteDirectory(attachPath);
			log.info("DEL_FILES | PATH:{} | DELETED:{}", attachPath, attachDeleted);
			if (!attachDeleted) {
				log.warn("{} | PATH:{}", ErrorCode.CLEANUP_ATTACH_DELETE_FAIL.toString(), attachPath);
			}
			return attachDeleted;
		} catch (Exception e) {
			log.error("{} | PATH:{}", ErrorCode.CLEANUP_ATTACH_DELETE_FAIL.toString(), attachPath, e);
			return false;
		}
	}

	private void deleteIndexAndSaveHistory(final String date, final String index, final String deleteType) {
		try {
			long total = indexService.countIndex(index);
			if (indexService.deleteIndices(index)) {
				insertDeleteHistory(date, deleteType, total);
				log.info("DEL_INDEX_OK | INDEX:{} | DATE:{}", index, date);
			} else {
				log.warn("{} | INDEX:{} | DATE:{}", ErrorCode.CLEANUP_INDEX_DELETE_FAIL.toString(), index, date);
			}
		} catch (Exception e) {
			log.error("{} | INDEX:{} | DATE:{}", ErrorCode.CLEANUP_INDEX_DELETE_FAIL.toString(), index, date, e);
		}
	}

	private void insertDeleteHistory(final String deleteDate, final String deleteType, final long deleteCount) {
		DeleteMessage deleteMessage = new DeleteMessage();
		deleteMessage.setDeleteDate(deleteDate);
		deleteMessage.setDeleteType(deleteType);
		deleteMessage.setDeleteCount(deleteCount);
		repository.insertDeleteHistory(deleteMessage);
	}

	public void runCleanupCapacity(final String dir, double targetUsageFraction) {
		validateCleanupCapacityArgs(dir, targetUsageFraction);
		List<Map<String, String>> indices = getIndicesOrNull();
		if (indices == null || indices.isEmpty()) {
			log.info("END_EMPTY | No more index to delete. Target: {}", fmt(targetUsageFraction));
			return;
		}

		for (Map<String, String> item : indices) {
			double before;
			try {
				before = currentUsageFraction(dir);
			} catch (Exception e) {
				log.error("{} | DIR:{}", ErrorCode.CLEANUP_DISK_USAGE_FAIL.toString(), dir, e);
				return;
			}
			if (before <= targetUsageFraction) {
				log.info("OK_USAGE | Disk Usage {} <= Target {}. Stop cleanup.", fmt(before), fmt(targetUsageFraction));
				return;
			}

			String index = Common.nvl(item.get("index"));
			String date = Common.nvl(item.get("date"));
			if (DateUtils.validDate(date, DateUtils.YYYYMMDD)) {
				delete(date, index, DELETE_TYPE_CAPACITY);
			}
		}
	}

	private void validateCleanupCapacityArgs(final String dir, final double targetUsageFraction) {
		if (targetUsageFraction <= 0.0 || targetUsageFraction >= 1.0) {
			throw new IllegalArgumentException("targetUsageFraction must be between 0 and 1 (exclusive)");
		}
		if (dir == null || dir.isBlank() || "/".equals(dir.trim())) {
			throw new IllegalArgumentException("Invalid DIR: " + dir);
		}
	}

	private List<Map<String, String>> getIndicesOrNull() {
		try {
			return indexService.getIndices();
		} catch (Exception e) {
			log.error("{}", ErrorCode.CLEANUP_INDEX_LIST_FAIL.toString(), e);
			return null;
		}
	}

	private boolean isRetentionTarget(final String date, final int term, final DateTime today) {
		if (!DateUtils.validDate(date, DateUtils.YYYYMMDD)) {
			return false;
		}
		DateTime dateTime = DateUtils.parseDateTimeYYYYMMDD(date);
		int daysDiff = Days.daysBetween(dateTime.withTimeAtStartOfDay(), today.withTimeAtStartOfDay()).getDays();
		return term < daysDiff;
	}

	private double currentUsageFraction(final String dir) {
		long total = fileProcessor.getTotalSpace(dir);
		long usable = fileProcessor.getUsableSpace(dir);
		long used = total - usable;
		return (total > 0) ? (double) used / (double) total : 0.0;
	}

	private String fmt(double v) {
		return String.format("%.2f", v * 100);
	}
}
