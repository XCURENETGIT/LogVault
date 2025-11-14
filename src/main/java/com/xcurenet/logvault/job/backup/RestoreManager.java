package com.xcurenet.logvault.job.backup;

import com.alibaba.fastjson2.JSONObject;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.Nullable;
import org.joda.time.DateTime;
import org.opensearch.data.client.orhlc.OpenSearchRestTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Log4j2
@Component
@RequiredArgsConstructor
public class RestoreManager {
	private final Config conf;
	protected final OpenSearchRestTemplate template;

	public boolean restore(final DateTime dateTime) throws IOException {
		final String date = dateTime.toString(DateUtils.F_YYYYMMDD);
		Path filePath = checkPath(date);
		if (filePath == null) return false;

		StopWatch sw = DateUtils.start();
		AtomicLong total = new AtomicLong(0L);
		final int BATCH_SIZE = 100;
		final IndexCoordinates ic = IndexCoordinates.of(conf.getIndexName() + date);
		final List<IndexQuery> batch = new ArrayList<>(BATCH_SIZE);

		IndexOperations ops = template.indexOps(ic);
		try (BufferedReader br = new BufferedReader(new InputStreamReader(Common.zipOpen(filePath), StandardCharsets.UTF_8))) {
			br.lines().forEach(line -> {
				JSONObject obj = JSONObject.parseObject(line);
				final String msgId = obj.getString("msgid");
				batch.add(new IndexQueryBuilder().withId(msgId).withSource(line).build());
				if (batch.size() >= BATCH_SIZE) {
					template.bulkIndex(batch, ic);
					batch.clear();
				}
				total.getAndIncrement();
				if (total.get() % (BATCH_SIZE * 5L) == 0) {
					log.info("RESTORE_IDX | indexed {} docs...", total);
				}
			});
			if (!batch.isEmpty()) {
				template.bulkIndex(batch, ic);
				batch.clear();
			}
			ops.refresh();
			log.info("RESTORE_IDX | DONE | date:{} | docs:{} | {}", date, total, DateUtils.stop(sw));
		} catch (IOException e) {
			log.error("RESTORE_IDX | {}", e.getMessage(), e);
			return false;
		}
		return true;
	}

	@Nullable
	private Path checkPath(String date) {
		if (!DateUtils.validDate(date, DateUtils.YYYYMMDD)) {
			throw new SecurityException("Invalid date format: " + date);
		}

		String filePathStr = Common.makeFilepath(conf.getBackupPath(), "index", date, date + ".zst");
		if (filePathStr == null) {
			log.warn("Backup path is null | {}", date);
			throw new SecurityException("Invalid File Path: " + date);
		}

		Path filePath = Paths.get(filePathStr).toAbsolutePath().normalize();
		try {
			Path base = Paths.get(conf.getBackupPath()).toAbsolutePath().normalize();
			Path baseReal = base.toRealPath(LinkOption.NOFOLLOW_LINKS);
			Path fileReal = filePath.toRealPath(LinkOption.NOFOLLOW_LINKS);
			if (!fileReal.startsWith(baseReal)) {
				throw new SecurityException("Invalid file path (path traversal?)");
			}
		} catch (IOException e) {
			log.warn("RESTORE_IDX | canonical check failed: {}", e.getMessage());
			return null;
		}

		if (!Files.exists(filePath)) {
			log.warn("Backup file does not exist | {}", filePath);
			return null;
		}
		return filePath;
	}
}
