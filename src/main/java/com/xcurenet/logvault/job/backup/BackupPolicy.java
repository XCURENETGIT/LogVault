package com.xcurenet.logvault.job.backup;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.opensearch.IndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;
import org.opensearch.data.client.orhlc.OpenSearchRestTemplate;
import org.opensearch.index.query.QueryBuilders;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchScrollHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 1. 백업 (날짜별 과거 1일 데이터 백업)
 * > 옵션
 * - 사용여부, 백업 경로
 * - 백업 경로는 필수로 아래에 포함되어야 함. (스냅샷을 쓸경우, 지금은 json으로 백업 파일에 저장)
 * 1) /etc/opensearch/opensearch.yml
 * 2) path.repo: ["/data01/backup/index"]
 * 3) sudo chown -R opensearch:opensearch /data01/backup
 * 4) sudo chmod 750 /data01/backup
 * > 실행
 * - 매일 새벽 3시에 실행
 * > 내용 :
 * - 백업 경로에 날짜별 백업
 * - OpenSearch 데이터 (json)
 * - 첨부 파일 (암호화 상태)
 * - 본문 파일 (암호화 상태)
 * - 백업의 경우 OpenSearch JsonObject를 단일 건으로 라인별 저장 (전체 텍스트는 압축 후 암호화 처리)
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class BackupPolicy {
	private final Config conf;
	private final IndexService indexService;
	protected final OpenSearchRestTemplate template;

	@Scheduled(cron = "0 0 3 * * *")
	private void backup() throws IOException {
		if (Common.isWindow()) return;

		DateTime yesterday = DateTime.now().minusDays(1);
		backupRun(yesterday);
	}

	public boolean backupRun(final DateTime dateTime) throws IOException {
		final String date = dateTime.toString(DateUtils.F_YYYYMMDD);
		log.info("BACKUP_INFO | isBackupEnable:{} | path:{}", conf.isBackupEnable(), conf.getBackupPath());
		if (!conf.isBackupEnable()) return false;
		FileUtils.forceMkdir(new File(conf.getBackupPath()));

		boolean result = indexBackup(date);
		if (result) { //인덱스 스냅샷이 정상 처리 되면 첨부, 본문도 백업
			attachBackup(date);
			return true;
		}
		return false;
	}

	public JSONArray getBackupList() throws IOException {
		JSONArray result = new JSONArray();
		String path = Common.makeFilepath(conf.getBackupPath(), "index");
		if (path == null) return result;

		File dir = new File(path);
		File[] backupDirs = dir.listFiles();
		if (backupDirs == null) return result;

		for (File backupDir : backupDirs) {
			log.info("BACKUP_INFO | path:{}", backupDir.getAbsolutePath());
			JSONObject item = new JSONObject();
			item.put("path", backupDir.getAbsolutePath());

			File[] file = backupDir.listFiles();
			if (file == null || file.length == 0) continue;

			item.put("count", getCount(file[0]));
			item.put("size", file[0].length());
			result.add(item);
		}
		return result;
	}

	private long getCount(File file) throws IOException {
		long lines = 0;
		try (InputStream fis = Common.zipOpen(file.toPath()); BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
			while (reader.readLine() != null) {
				lines++;
			}
		}
		return lines;
	}

	private void attachBackup(final String yesterday) throws IOException {
		File src = new File(Objects.requireNonNull(Common.makeFilepath(conf.getAttachRoot(), yesterday))); //첨부, 본문 저장 원본 위치
		File dest = new File(Objects.requireNonNull(Common.makeFilepath(conf.getBackupPath(), "attach"))); //첨부, 본문 백업 위치
		if (src.exists()) {
			FileUtils.forceMkdir(dest);
			StopWatch sw = DateUtils.start();
			FileUtils.copyDirectory(src, dest);
			log.info("BACKUP_ATT | {} | {} | {}", src, dest, DateUtils.stop(sw));
		}
	}

	/**
	 * 개별 DOC 문서를 zst 압축파일에 json object로 라인별 저장
	 */
	private boolean indexBackup(final String yesterday) throws IOException {
		if (!isExistIndices(yesterday)) return false;

		StopWatch sw = DateUtils.start();
		long total = 0;
		int batchSize = 100;
		long scrollTtlMs = 600000L;

		String path = Common.makeFilepath(conf.getBackupPath(), "index", yesterday);
		if (path == null) return false;
		FileUtils.forceMkdir(new File(path));

		String indexName = conf.getIndexName() + yesterday;
		IndexCoordinates indexCoordinates = IndexCoordinates.of(indexName);
		String outFile = Common.makeFilepath(path, yesterday + ".zst");
		try (FileOutputStream out = new FileOutputStream(Objects.requireNonNull(outFile));
		     ZstdOutputStream zstd = new ZstdOutputStream(out).setLevel(12);
		     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zstd, StandardCharsets.UTF_8), 1 << 20)) {

			NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
					.withQuery(QueryBuilders.matchAllQuery())
					.withPageable(PageRequest.of(0, batchSize))
					.build();

			SearchScrollHits<Document> scrollHits = template.searchScrollStart(scrollTtlMs, searchQuery, Document.class, indexCoordinates);
			String scrollId = scrollHits.getScrollId();
			try {
				while (!scrollHits.isEmpty()) {
					for (SearchHit<Document> hit : scrollHits.getSearchHits()) {
						String json = hit.getContent().toJson();
						writer.write(json);
						writer.write('\n');
						total++;
					}

					assert scrollId != null;
					scrollHits = template.searchScrollContinue(scrollId, scrollTtlMs, Document.class, indexCoordinates);
					scrollId = scrollHits.getScrollId();
					if (total % (batchSize * 10L) == 0) {
						log.info("BACKUP_IDX | {} docs", total);
					}
				}
			} catch (Exception e) {
				log.error("BACKUP_IDX | {}", e.getMessage(), e);
				return false;
			} finally {
				if (scrollId != null) {
					try {
						template.searchScrollClear(scrollId);
					} catch (Exception ignore) {
					}
				}
			}
			log.info("BACKUP_IDX | DONE | {} docs | {}", total, DateUtils.stop(sw));
			return true;
		} catch (Exception e) {
			log.error("BACKUP_IDX | {}", e.getMessage(), e);
			return false;
		}
	}

	private boolean isExistIndices(final String yesterday) {
		List<Map<String, String>> indices = indexService.getIndices(); //생성된 인덱스가 있는지 체크
		if (indices == null || indices.isEmpty()) {
			log.info("END_EMPTY | No more index to backup.");
			return false;
		}
		boolean isExists = false;
		for (Map<String, String> item : indices) {
			String date = Common.nvl(item.get("date"));
			if (!DateUtils.validDate(date, DateUtils.YYYYMMDD)) continue;
			if (Common.isEquals(date, yesterday)) {
				isExists = true;
				break;
			}
		}
		if (!isExists) { // 백업할 인덱스가 있는지?
			log.info("BACKUP_IDX | NOT FOUND DATA FOR YESTERDAY {}", yesterday);
			return false;
		}
		return true;
	}


	/**
	 * 스냅샷 백업 (스냅샷의 경우 설정 필수, 권한 등의 필요성으로 사용하지 않음)
	 */
	private boolean indexSnapshot(final String yesterday) {
		try {
			if (isExistIndices(yesterday)) return false;


			StopWatch sw = DateUtils.start();
			String location = Common.makeFilepath(conf.getBackupPath(), "index");
			if (location == null) return false;

			if (indexService.existsSnapshot(yesterday)) { //스냅샷이 이미 있다면 처리하지 않음.
				log.info("BACKUP_IDX | EXIST DATA FOR YESTERDAY {}", yesterday);
				return false;
			}

			FileUtils.forceMkdir(new File(location));
			indexService.createRepository(location);  //Repo 생성 (이미 있어도 상관없음)
			indexService.createDailySnapshot(yesterday); //어제 날짜의 스냅샷 생성

			log.info("BACKUP_IDX | {} | {} | {}", location, yesterday, DateUtils.stop(sw));
			return true;
		} catch (Exception e) {
			log.error("BACKUP_IDX | {}", e.getMessage(), e);
		}
		return false;
	}
}
