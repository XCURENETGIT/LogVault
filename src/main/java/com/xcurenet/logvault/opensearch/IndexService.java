package com.xcurenet.logvault.opensearch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xcurenet.common.utils.DateUtils;
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
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class IndexService {
	private final static String ENDPOINT = "/_cat/indices/%s*?format=json&h=index,docs.count,store.size,pri.store.size&bytes=b&s=creation.date";
	protected final OpenSearchRestTemplate template;
	private final Config conf;

	public <T> void index(final T data, final String msgId, final String indexName) throws IndexerException {
		StopWatch sw = DateUtils.start();
		try {
			template.save(data, IndexCoordinates.of(indexName));
		} catch (Exception e) {
			throw new IndexerException(e);
		} finally {
			log.info("[MG_INDEX] {} | {} | {}", msgId, indexName, DateUtils.stop(sw));
		}
	}

	public SearchHits<EmassDoc> selectOldFiles() {
		NativeSearchQuery query = new NativeSearchQueryBuilder().withQuery(QueryBuilders.queryStringQuery("*:*")).withTrackTotalHits(true).withPageable(PageRequest.of(0, 1000)).withSort(Sort.by(Sort.Order.asc("@timestamp"))).build();
		return template.search(query, EmassDoc.class);
	}

	public void deleteDoc(final String index, final String id) {
		//if (Common.isWindow()) return;
		boolean readOnly = isReadOnly(index);
		if (readOnly) setReadOnlyStrict(index, false);

		log.info("[MG_DELETE] index={}, id={}", index, id);
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
			throw new IllegalArgumentException("indexName must not be blank");
		}

		String trimmed = indexName.trim();
		if ("*".equals(trimmed) || "_all".equalsIgnoreCase(trimmed)) {
			throw new IllegalArgumentException("Refusing to delete '*' or '_all'");
		}
		if (trimmed.startsWith(".")) { // 시스템/숨김 인덱스 가드 (필요 시 allowSystem 플래그로 완화)
			throw new IllegalArgumentException("Refusing to delete system/hidden indices");
		}

		return template.execute((RestHighLevelClient client) -> {
			try {
				UpdateSettingsRequest unlock = new UpdateSettingsRequest(indexName);
				unlock.settings(Settings.builder().putNull("index.blocks.read_only_allow_delete").put("index.blocks.read_only", false));
				client.indices().putSettings(unlock, RequestOptions.DEFAULT);
			} catch (Exception e) {
				log.debug("[IDX_UNLOCK_FAIL] index={} err={}", indexName, e.toString());
			}

			DeleteIndexRequest del = new DeleteIndexRequest(indexName).indicesOptions(org.opensearch.action.support.IndicesOptions.fromOptions(
					/* ignoreUnavailable */ true,
					/* allowNoIndices    */ true,
					/* expandWildcardsOpen  */ true,
					/* expandWildcardsClosed*/ true));

			try {
				AcknowledgedResponse resp = client.indices().delete(del, RequestOptions.DEFAULT);
				boolean ok = resp.isAcknowledged();
				log.info("[IDX_DELETE] index={} acknowledged={}", indexName, ok);
				return ok;
			} catch (OpenSearchStatusException ose) {
				int status = ose.status().getStatus();
				if (status == 404) { // 404: 이미 없음 → 성공 간주
					log.info("[IDX_DELETE] index={} not found (treated OK)", indexName);
					return true;
				}
				log.warn("[IDX_DELETE_FAIL] index={} status={} err={}", indexName, status, ose.toString());
				return false;
			} catch (Exception e) {
				log.warn("[IDX_DELETE_FAIL] index={} err={}", indexName, e.toString());
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
			Request req = new Request("GET", String.format(ENDPOINT, conf.getIndexName()));
			Response resp = low.performRequest(req);
			String json = EntityUtils.toString(resp.getEntity());
			List<Map<String, String>> list = new ObjectMapper().readValue(json, new TypeReference<>() {});
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


//	private void execute(final String index) {
//		template.execute(client -> {
//			RestClient low = client.getLowLevelClient();
//
//			// 1) unlock
//			Request r1 = new Request("PUT", "/" + index + "/_settings");
//			r1.setJsonEntity("""
//					{
//					  "index.blocks.read_only_allow_delete": null,
//					  "index.blocks.read_only": false
//					}
//					""");
//			low.performRequest(r1);
//			return null;
//		});
//	}
}