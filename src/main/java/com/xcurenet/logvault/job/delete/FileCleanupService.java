package com.xcurenet.logvault.job.delete;

import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.fs.FileProcessor;
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
	private final IndexService indexService;
	private final FileProcessor fileProcessor;
	private final Config conf;

	public static void main(String[] args) {
		int term = 365;
		DateTime today = DateTime.now();
		String date = "20241110";
		if (!DateUtils.validDate(date, DateUtils.YYYYMMDD)) return;

		DateTime dateTime = DateUtils.parseDateTimeYYYYMMDD(date);
		int daysDiff = Days.daysBetween(dateTime.withTimeAtStartOfDay(), today.withTimeAtStartOfDay()).getDays();
		System.out.println(term + " < " + daysDiff);
	}

	public void runCleanup(final int term) {
		List<Map<String, String>> indices = indexService.getIndices();
		if (indices == null || indices.isEmpty()) {
			log.info("END_EMPTY | No more index to delete.");
			return;
		}

		DateTime today = DateTime.now();
		for (Map<String, String> item : indices) {
			String date = Common.nvl(item.get("date"));
			if (!DateUtils.validDate(date, DateUtils.YYYYMMDD)) continue;

			DateTime dateTime = DateUtils.parseDateTimeYYYYMMDD(date);
			int daysDiff = Days.daysBetween(dateTime.withTimeAtStartOfDay(), today.withTimeAtStartOfDay()).getDays();
			if (term < daysDiff) {
				delete(date, Common.nvl(item.get("index")));
			}
		}
	}

	private void delete(String date, String index) {
		boolean attachDeleted = fileProcessor.deleteDirectory(Common.makeFilepath(conf.getAttachRoot(), date));
		log.info("DEL_FILES | Path:{} | AttachDeleted:{}", Common.makeFilepath(conf.getAttachRoot(), date), attachDeleted);
		if (attachDeleted) {
			if (indexService.deleteIndices(index)) { //인덱스 삭제
				log.info("DEL_INDEX | Index:{} | Date:{}", index, DateUtils.parseDateTimeYYYYMMDD(date));
			} else {
				log.warn("DEL_INDEX | Index:{} | Date:{}", index, DateUtils.parseDateTimeYYYYMMDD(date));
			}
		}
	}


	public void runCleanup(final String dir, double targetUsageFraction) {
		if (targetUsageFraction <= 0.0 || targetUsageFraction >= 1.0) {
			throw new IllegalArgumentException("targetUsageFraction must be between 0 and 1 (exclusive)");
		}
		if (dir == null || dir.isBlank() || "/".equals(dir.trim())) {
			throw new IllegalArgumentException("Invalid DIR: " + dir);
		}


		List<Map<String, String>> indices = indexService.getIndices();
		if (indices == null || indices.isEmpty()) {
			log.info("END_EMPTY | No more index to delete. Target: {}", fmt(targetUsageFraction));
			return;
		}


		for (Map<String, String> item : indices) {
			double before = currentUsageFraction(dir);
			if (before <= targetUsageFraction) {
				log.info("OK_USAGE | Disk Usage {} <= Target {}. Stop cleanup.", fmt(before), fmt(targetUsageFraction));
				return;
			}

			String index = Common.nvl(item.get("index"));
			String date = Common.nvl(item.get("date"));
			if (DateUtils.validDate(date, DateUtils.YYYYMMDD)) {
				delete(date, index);
			}
		}
	}

	private double currentUsageFraction(final String dir) {
		long total = fileProcessor.getTotalSpace(dir);
		long usable = fileProcessor.getUsableSpace(dir);      // 남은 공간
		long used = total - usable;                // 사용 중인 공간
		return (total > 0) ? (double) used / (double) total : 0.0;
	}

	private String fmt(double v) {
		return String.format("%.2f", v * 100);
	}
}
