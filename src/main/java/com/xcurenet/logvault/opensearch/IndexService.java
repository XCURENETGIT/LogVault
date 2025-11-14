package com.xcurenet.logvault.opensearch;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.common.utils.ExFactory;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.exception.IndexerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.http.util.EntityUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.opensearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.opensearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.opensearch.client.*;
import org.opensearch.common.settings.Settings;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;
import org.opensearch.data.client.orhlc.OpenSearchRestTemplate;
import org.opensearch.index.query.QueryBuilders;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Log4j2
@Service
@RequiredArgsConstructor
public class IndexService {

	private static final String SNAPSHOT_CREATE_API = "/_snapshot/local_backup/%s";
	private static final String REPO_CREATE_API = "/_snapshot/local_backup";
	private static final String SNAPSHOT_LIST_API = "/_snapshot/local_backup/_all";
	private static final String SNAPSHOT_EXIST_API = "/_snapshot/local_backup/%s";
	private static final String SNAPSHOT_RESTORE_API = "/_snapshot/local_backup/%s/_restore?wait_for_completion=true";

	private final static String INDEX_STATUS = "/_cat/indices/%s*?format=json&h=index,health,status,uuid,docs.count,store.size,pri.store.size&bytes=b&s=creation.date";
	private final static String CLUSTER_HEALTH = "/_cluster/health?pretty";
	private final static String SHARD_STATUS = "/_cat/shards/%s*?format=json";

	protected final OpenSearchRestTemplate template;
	private final Config conf;
	private final ObjectMapper mapper = new ObjectMapper();

	public <T> T get(final String msgId, final Class<T> clazz, final String indexName) {
		return template.get(msgId, clazz, IndexCoordinates.of(indexName));
	}

	public <T> void index(final T data, final String indexName) throws IndexerException {
		StopWatch sw = DateUtils.start();
		try {
			if (indexName == null) {
				throw ExFactory.ex(IndexerException::new, ErrorCode.INDEX_NAME_NULL, Map.of("index", "null", "size", getDataByteSize(data)));
			}
			if (data == null) {
				throw ExFactory.ex(IndexerException::new, ErrorCode.INDEX_DATA_NULL, Map.of("index", indexName, "data", "null"));
			}
			template.save(data, IndexCoordinates.of(indexName));
		} catch (Exception e) {
			StringWriter stringWriter = new StringWriter();
			e.printStackTrace(new PrintWriter(stringWriter));
			boolean refused = stringWriter.toString().lines().anyMatch(line -> line.contains("Connection refused"));
			if (refused) {
				throw ExFactory.ex(IndexerException::new, ErrorCode.INDEX_CONNECT_FAIL, Map.of("host", conf.getOpensearchRestUris()));
			} else {
				throw ExFactory.ex(IndexerException::new, ErrorCode.INDEX_SAVE_FAIL, Map.of("index", Optional.ofNullable(indexName), "size", getDataByteSize(data)), e);
			}
		}
		log.info("MG_INDEX | {} | SIZE:{} | {}", indexName, Common.convertFileSize(getDataByteSize(data)), DateUtils.stop(sw));
	}

	private int getDataByteSize(Object data) {
		try {
			return mapper.writeValueAsBytes(data).length;
		} catch (Exception e) {
			return -1;
		}
	}


	public void updatePrivacyAndKeyword(final String indexName, final String id, final List<Map<String, Object>> privacyInfoDoc, final Map<String, Object> keywordInfoDoc) {
		Map<String, Object> partial = new HashMap<>();
		if (privacyInfoDoc != null) partial.put("privacy_info", privacyInfoDoc);
		if (keywordInfoDoc != null) partial.put("keyword_info", keywordInfoDoc);

		UpdateQuery uq = UpdateQuery.builder(id).withIndex("emass").withDocument(Document.from(partial)).withDocAsUpsert(false).build();
		template.update(uq);
	}

	public SearchHits<EmassDoc> selectOldFiles() {
		NativeSearchQuery query = new NativeSearchQueryBuilder().withQuery(QueryBuilders.queryStringQuery("*:*")).withTrackTotalHits(true).withPageable(PageRequest.of(0, 1000)).withSort(Sort.by(Sort.Order.asc("@timestamp"))).build();
		return template.search(query, EmassDoc.class);
	}

	public void deleteDoc(final String index, final String id) {
		//if (Common.isWindow()) return;
		boolean readOnly = isReadOnly(index);
		if (readOnly) setReadOnlyStrict(index, false);

		log.info("MG_DELETE | index={}, id={}", index, id);
		template.delete(id, IndexCoordinates.of(index));

		if (readOnly) setReadOnlyStrict(index, true);
	}

	/**
	 * opensearch index 삭제
	 *
	 * @param indexName 인덱스 이름 (ex: emass-20251016)
	 * @return 삭제 여부
	 */
	public boolean deleteIndices(final String indexName) {
		if (!StringUtils.hasText(indexName)) {
			throw ExFactory.ex(IndexerException::new, ErrorCode.INDEX_DEL_NAME_NULL, Map.of("context", indexName));
		}

		String trimmed = indexName.trim();
		if ("*".equals(trimmed) || "_all".equalsIgnoreCase(trimmed)) {
			throw ExFactory.ex(IndexerException::new, ErrorCode.INDEX_DEL_INVALID, Map.of("context", indexName));
		}
		if (trimmed.startsWith(".")) { // 시스템/숨김 인덱스 가드 (필요 시 allowSystem 플래그로 완화)
			throw ExFactory.ex(IndexerException::new, ErrorCode.INDEX_DEL_SYSTEM, Map.of("context", indexName));
		}

		return template.execute((RestHighLevelClient client) -> {
			try {
				UpdateSettingsRequest unlock = new UpdateSettingsRequest(indexName);
				unlock.settings(Settings.builder().putNull("index.blocks.read_only_allow_delete").put("index.blocks.read_only", false));
				client.indices().putSettings(unlock, RequestOptions.DEFAULT);
			} catch (Exception e) {
				log.debug("IDX_UNLOCK_FAIL | index={} err={}", indexName, e.toString());
			}

			DeleteIndexRequest del = new DeleteIndexRequest(indexName).indicesOptions(org.opensearch.action.support.IndicesOptions.fromOptions(
					/* ignoreUnavailable */ true,
					/* allowNoIndices    */ true,
					/* expandWildcardsOpen  */ true,
					/* expandWildcardsClosed*/ true));

			try {
				AcknowledgedResponse resp = client.indices().delete(del, RequestOptions.DEFAULT);
				boolean ok = resp.isAcknowledged();
				log.info("IDX_DELETE | index={} acknowledged={}", indexName, ok);
				return ok;
			} catch (OpenSearchStatusException ose) {
				int status = ose.status().getStatus();
				if (status == 404) { // 404: 이미 없음 → 성공 간주
					log.info("IDX_DELETE | index={} not found (treated OK)", indexName);
					return true;
				}
				log.warn("{} | {} | index={} status={} err={}", ErrorCode.INDEX_DEL_FAIL, ErrorCode.fromCode(ErrorCode.INDEX_DEL_FAIL), indexName, status, ose.toString());
				return false;
			} catch (Exception e) {
				log.warn("{} | {} | index={} err={}", ErrorCode.INDEX_DEL_FAIL, ErrorCode.fromCode(ErrorCode.INDEX_DEL_FAIL), indexName, e.toString());
				return false;
			}
		});
	}


	private boolean isReadOnly(final String index) {
		return template.execute((RestHighLevelClient client) -> {
			GetSettingsRequest getReq = new GetSettingsRequest().indices(index);
			GetSettingsResponse getResp = client.indices().getSettings(getReq, RequestOptions.DEFAULT);
			return "true".equalsIgnoreCase(getResp.getIndexToSettings().get(index).get("index.blocks.read_only"));
		});
	}

	private void setReadOnlyStrict(final String index, final boolean readOnly) {
		template.execute(client -> {
			UpdateSettingsRequest req = new UpdateSettingsRequest(index);
			req.settings(Settings.builder().put("index.blocks.read_only", readOnly).build());
			client.indices().putSettings(req, RequestOptions.DEFAULT);
			return null;
		});
	}

	/**
	 * EMASS 인덱스(일별) 목록 > 사이즈
	 */
	public List<Map<String, String>> getIndices() {
		return template.execute(client -> {
			RestClient low = client.getLowLevelClient();
			Request req = new Request("GET", String.format(INDEX_STATUS, conf.getIndexName()));
			Response resp = low.performRequest(req);
			String json = EntityUtils.toString(resp.getEntity());
			List<Map<String, String>> list = new ObjectMapper().readValue(json, new TypeReference<>() {
			});
			list.sort(Comparator.comparing(m -> {
				try {
					String dateStr = m.get("index").replaceAll(".*-(\\d{8})$", "$1");
					m.put("date", dateStr);
					return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
				} catch (Exception e) {
					return LocalDate.MAX;
				}
			}));
			return list;
		});
	}

	public JSONObject getClusterHealth() {
		return template.execute(client -> {
			Response resp = client.getLowLevelClient().performRequest(new Request("GET", CLUSTER_HEALTH));
			return JSON.parseObject(EntityUtils.toString(resp.getEntity()));
		});
	}

	public JSONArray getShardStatus() {
		return template.execute(client -> {
			Response resp = client.getLowLevelClient().performRequest(new Request("GET", String.format(SHARD_STATUS, conf.getIndexName())));
			return JSON.parseArray(EntityUtils.toString(resp.getEntity()));
		});
	}

	/**
	 * 스냅샷 저장소(repository) 등록
	 */
	public JSONObject createRepository(final String location) {
		return template.execute(client -> {
			Request req = new Request("PUT", REPO_CREATE_API);
			req.setJsonEntity("""
					{
						"type": "fs",
						"settings": {
							"location": "%s",
							"compress": true
						}
					}
					""".formatted(location));

			Response resp = client.getLowLevelClient().performRequest(req);
			String json = EntityUtils.toString(resp.getEntity());
			log.info("📦 Repository created | {} | {}", REPO_CREATE_API, json);
			return JSON.parseObject(json);
		});
	}

	/**
	 * 일자별 스냅샷 생성
	 */
	public JSONObject createDailySnapshot(final String date) {
		if (!DateUtils.validDate(date, DateUtils.YYYYMMDD)) {
			throw new SecurityException("Invalid date format: " + date);
		}

		return template.execute(client -> {
			String indexName = conf.getIndexName() + date.replace("-", "");
			String snapshotName = "snap-" + date;

			Request req = new Request("PUT", String.format(SNAPSHOT_CREATE_API, snapshotName));
			req.setJsonEntity("""
					{
						"indices": "%s",
						"include_global_state": false
					}
					""".formatted(indexName));

			Response resp = client.getLowLevelClient().performRequest(req);
			String json = EntityUtils.toString(resp.getEntity());
			log.info("💾 Snapshot created | {} | {}", String.format(SNAPSHOT_CREATE_API, snapshotName), json);
			return JSON.parseObject(json);
		});
	}

	/**
	 * 스냅샷 존재여부
	 */
	public boolean existsSnapshot(String date) {
		if (!DateUtils.validDate(date, DateUtils.YYYYMMDD)) {
			throw new SecurityException("Invalid date format: " + date);
		}

		return template.execute(client -> {
			String snapshotName = "snap-" + date;
			try {
				Request req = new Request("GET", String.format(SNAPSHOT_EXIST_API, snapshotName));
				Response resp = client.getLowLevelClient().performRequest(req);
				int status = resp.getStatusLine().getStatusCode();
				if (status == 200) {
					log.info("✅ Snapshot [{}] already exists", snapshotName);
					return true;
				}
			} catch (ResponseException e) {
				if (e.getResponse().getStatusLine().getStatusCode() == 404) {
					log.info("❌ Snapshot [{}] not found", snapshotName);
					return false;
				}
			} catch (Exception e) {
				log.warn("SNAPSHOT ERROR | ", e);
			}
			return false;
		});
	}

	public JSONObject getSnapshotList() {
		return template.execute(client -> {
			Request req = new Request("GET", SNAPSHOT_LIST_API);

			Response resp = client.getLowLevelClient().performRequest(req);
			String json = EntityUtils.toString(resp.getEntity());
			log.info("📄 Snapshot list | {}", json);

			return JSON.parseObject(json);
		});
	}

	public JSONObject restoreSnapshot(final String date) {
		if (!DateUtils.validDate(date, DateUtils.YYYYMMDD)) {
			throw new SecurityException("Invalid date format: " + date);
		}

		String snapshotName = "snap-" + date;
		return template.execute(client -> {
			StopWatch sw = DateUtils.start();
			String body = """
					{
					  "indices": "%s",
					  "include_global_state": false,
					  "ignore_unavailable": true,
					  "include_aliases": true
					}
					""".formatted(conf.getIndexName() + date);

			Request req = new Request("POST", String.format(SNAPSHOT_RESTORE_API, snapshotName));
			req.setJsonEntity(body);

			Response resp = client.getLowLevelClient().performRequest(req);
			String json = EntityUtils.toString(resp.getEntity());
			log.info("🔁 Restore requested | {} | {} | {}", snapshotName, json, DateUtils.stop(sw));
			return JSON.parseObject(json);
		});
	}
}