package com.xcurenet.logvault.module.reprocess;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.types.IP;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.loader.type.UserInfo;
import com.xcurenet.logvault.module.util.InsaManager;
import com.xcurenet.logvault.opensearch.EmassDoc;
import com.xcurenet.logvault.opensearch.IndexService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;
import org.opensearch.data.client.orhlc.OpenSearchRestTemplate;
import org.opensearch.index.query.QueryBuilders;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchScrollHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class ReProcessInsa {
	protected final Config conf;
	protected final InsaManager insaManager;
	protected final IndexService indexService;
	protected final OpenSearchRestTemplate template;

	public Response reProcessUser(LocalDate start, LocalDate end) {
		Response totalResult = new Response(0, 0, 0);
		log.info("REPROCESS RANGE START | {} ~ {}", start, end);
		while (!start.isAfter(end)) {
			String dateStr = start.format(DateUtils.YYYYMMDD_F);
			log.info("REPROCESS DATE START | {}", dateStr);
			try {
				Response dayResult = reProcessUser(dateStr);
				totalResult.total += dayResult.total;
				totalResult.success += dayResult.success;
				totalResult.fail += dayResult.fail;
			} catch (Exception e) {
				log.error("REPROCESS DATE FAIL | {}", dateStr, e);
			}
			start = start.plusDays(1);
		}
		log.info("REPROCESS RANGE DONE | Total:{} | Success:{} | Fail:{}", totalResult.total, totalResult.success, totalResult.fail);
		return totalResult;
	}

	private Response reProcessUser(final String date) {
		int batchSize = 500;
		long scrollTtlMs = 600000L;
		Response result = new Response(0, 0, 0);

		String indexName = conf.getIndexName() + date;
		IndexCoordinates indexCoordinates = IndexCoordinates.of(indexName);
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder() //query builder
				.withQuery(QueryBuilders.matchAllQuery()) //query
				.withPageable(PageRequest.of(0, batchSize)) //page
				.withSourceFilter(new FetchSourceFilter(new String[]{"network.src_ip"}, null)).build();
		SearchScrollHits<Document> scrollHits = template.searchScrollStart(scrollTtlMs, searchQuery, Document.class, indexCoordinates);
		String scrollId = scrollHits.getScrollId();
		try {
			while (!scrollHits.isEmpty()) {
				for (SearchHit<Document> hit : scrollHits.getSearchHits()) {
					result.total++;
					String json = hit.getContent().toJson();
					String docId = hit.getId();
					IP srcip = getSourceIp(hit.getContent());
					log.info("REPROCESS | DATE:{} | MSGID:{} | SRCIP:{}", date, docId, srcip);
					if (srcip == null) {
						result.fail++;
						log.warn("{} | DOC:{}", ErrorCode.INSA_SIP_NULL.toString(), json);
						continue;
					}

					EmassDoc.User user = new EmassDoc.User();
					user.setIp(srcip.toCanonicalAddr());
					try {
						UserInfo info = insaManager.getUserInfoByIp(srcip);
						if (info != null) {
							user.setId(info.getUserId());
							user.setName(info.getName());
							user.setCeo(Common.isEquals(info.getCeo(), "Y"));
							user.setDeptCode(info.getDeptCd());
							user.setDeptName(info.getDeptNm());
							user.setJikgubCode(info.getJikgubCd());
							user.setJikgubName(info.getJikgubNm());
						}

						if (indexService.updateUser(indexName, docId, user)) result.success++;
						else result.fail++;
					} catch (Exception e) {
						result.fail++;
						log.warn("{} | SRCIP={} err={}", ErrorCode.INSA_MAPPING_FAIL.toString(), srcip, e.toString(), e);
					}
				}
				assert scrollId != null;
				scrollHits = template.searchScrollContinue(scrollId, scrollTtlMs, Document.class, indexCoordinates);
				scrollId = scrollHits.getScrollId();
			}
		} catch (Exception e) {
			log.error("REPROCESS | {}", e.getMessage(), e);
		} finally {
			if (scrollId != null) {
				try {
					template.searchScrollClear(scrollId);
				} catch (Exception ignore) {
				}
			}
			log.info("REPROCESS DONE | Total:{} | Success:{} | Fail:{}", result.total, result.success, result.fail);
		}
		return result;
	}

	private IP getSourceIp(final Map<String, Object> map) {
		try {
			@SuppressWarnings("unchecked") Map<String, Object> network = (Map<String, Object>) map.get("network");
			return new IP((String) network.get("src_ip"));
		} catch (Exception e) {
			log.warn("{}", ErrorCode.INSA_SIP_NULL.toString(), e);
		}
		return null;
	}

	@Data
	public static class Response {
		private long total;
		private long success;
		private long fail;

		public Response(long total, long success, long fail) {
			this.total = total;
			this.success = success;
			this.fail = fail;
		}
	}
}