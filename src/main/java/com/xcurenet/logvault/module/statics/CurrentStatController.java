package com.xcurenet.logvault.module.statics;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stat")
@RequiredArgsConstructor
public class CurrentStatController {

	private final StatService statService;

	/* ==================================================
	 * 현재 분 단위 통계 (전체 캐시)
	 * GET /api/stat/minute
	 * ================================================== */
	@GetMapping("/minute")
	public Map<String, StatResponseDTO> currentMinute() {
		return buildFromCache(statService.getMinuteCache().asMap());
	}

	/* ==================================================
	 * 현재 10분 단위 통계 (전체 캐시)
	 * GET /api/stat/10min
	 * ================================================== */
	@GetMapping("/10min")
	public Map<String, StatResponseDTO> currentTenMin() {
		return buildFromCache(statService.getTenMinCache().asMap());
	}

	/* ==================================================
	 * 현재 시간 단위 통계 (전체 캐시)
	 * GET /api/stat/hour
	 * ================================================== */
	@GetMapping("/hour")
	public Map<String, StatResponseDTO> currentHour() {
		return buildFromCache(statService.getHourCache().asMap());
	}

	/* ==================================================
	 * 현재 분 기준 사용자 TOP 10
	 * GET /api/stat/minute/top
	 * ================================================== */
	@GetMapping("/minute/top")
	public Map<String, StatDTO> currentMinuteTop() {
		TimeSlotStat stat = statService.getCurrentMinuteStat();
		if (stat == null) {
			return Map.of();
		}
		return sortByUserTotal(stat.getUserMap(), 10);
	}

	/* ==================================================
	 * 관리자: 캐시 사이즈 조회
	 * GET /api/stat/cache/size
	 * ================================================== */
	@GetMapping("/cache/size")
	public Map<String, Long> cacheSize() {
		return Map.of("minute", statService.getMinuteCache().estimatedSize(), "tenMin", statService.getTenMinCache().estimatedSize(), "hour", statService.getHourCache().estimatedSize());
	}

	/* ==================================================
	 * 관리자: 캐시 초기화
	 * DELETE /api/stat/cache/clear
	 * ================================================== */
	@DeleteMapping("/cache/clear")
	public void clearCache() {
		statService.getMinuteCache().invalidateAll();
		statService.getTenMinCache().invalidateAll();
		statService.getHourCache().invalidateAll();
	}

	/* ==================================================
	 * ============ 내부 공통 로직 ============
	 * ================================================== */

	/**
	 * 캐시(Map<timeKey, TimeSlotStat>) → 응답 Map 변환
	 */
	private Map<String, StatResponseDTO> buildFromCache(Map<String, TimeSlotStat> cache) {
		if (cache == null || cache.isEmpty()) {
			return Map.of();
		}

		return cache.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> build(e.getValue()), (x, y) -> x, LinkedHashMap::new));
	}

	/**
	 * TimeSlotStat → StatResponseDTO 변환
	 */
	private StatResponseDTO build(TimeSlotStat stat) {
		if (stat == null) {
			return empty();
		}

		return new StatResponseDTO(toSimple(stat.getTotal()), sortByUserTotal(stat.getUserMap(), 0) // 0 = 전체 사용자
		);
	}

	/**
	 * 사용자 통계 정렬 (total desc, 동률 시 key asc)
	 * limit == 0 → 전체
	 */
	private Map<String, StatDTO> sortByUserTotal(Map<String, StatCounter> userMap, int limit) {
		if (userMap == null || userMap.isEmpty()) {
			return Map.of();
		}

		return userMap.entrySet().stream().sorted((a, b) -> {
			long at = a.getValue().getTotal().get();
			long bt = b.getValue().getTotal().get();
			int cmp = Long.compare(bt, at);
			if (cmp != 0) return cmp;
			return a.getKey().compareTo(b.getKey());
		}).limit(limit > 0 ? limit : Long.MAX_VALUE).collect(Collectors.toMap(Map.Entry::getKey, e -> toSimple(e.getValue()), (x, y) -> x, LinkedHashMap::new));
	}

	/**
	 * StatCounter → StatDTO 단순화
	 */
	private StatDTO toSimple(StatCounter c) {
		return new StatDTO(c.getTotal().get(), c.getPrompt().get(), c.getAttach().get());
	}

	/**
	 * 빈 응답
	 */
	private StatResponseDTO empty() {
		return new StatResponseDTO(new StatDTO(0, 0, 0), Map.of());
	}
}
