package com.xcurenet.logvault.module.statics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Data
@Service
public class StatService {
	public final Cache<String, TimeSlotStat> minuteCache = Caffeine.newBuilder().expireAfterWrite(70, TimeUnit.MINUTES).maximumSize(100_000).build();
	public final Cache<String, TimeSlotStat> tenMinCache = Caffeine.newBuilder().expireAfterWrite(6, TimeUnit.HOURS).maximumSize(50_000).build();
	public final Cache<String, TimeSlotStat> hourCache = Caffeine.newBuilder().expireAfterWrite(2, TimeUnit.DAYS).maximumSize(20_000).build();

	public void processEvent(ScanData data) {
		EmassDoc doc = data.getEmassDoc();

		String userId = null;
		if (doc.getUser() != null) userId = doc.getUser().getId();
		if (userId == null) userId = doc.getNetwork().getSrcIp();

		String minuteKey = timeKey(doc.getTimestamp().getTime(), ChronoUnit.MINUTES);
		String tenMinKey = timeKey10Min(doc.getTimestamp().getTime());
		String hourKey = timeKey(doc.getTimestamp().getTime(), ChronoUnit.HOURS);

		if (doc.getBody() != null && doc.getBody().getText() != null) {
			minuteCache.get(minuteKey, k -> new TimeSlotStat()).incPrompt(userId);
			tenMinCache.get(tenMinKey, k -> new TimeSlotStat()).incPrompt(userId);
			hourCache.get(hourKey, k -> new TimeSlotStat()).incPrompt(userId);
		}

		if (doc.getAttachCount() > 0) {
			for (int i = 0; i < doc.getAttachCount(); i++) {
				minuteCache.get(minuteKey, k -> new TimeSlotStat()).incAttach(userId);
				tenMinCache.get(tenMinKey, k -> new TimeSlotStat()).incAttach(userId);
				hourCache.get(hourKey, k -> new TimeSlotStat()).incAttach(userId);
			}
		}
	}

	public String currentMinuteKey() {
		return timeKey(System.currentTimeMillis(), ChronoUnit.MINUTES);
	}

	public String currentTenMinKey() {
		return timeKey10Min(System.currentTimeMillis());
	}

	public String currentHourKey() {
		return timeKey(System.currentTimeMillis(), ChronoUnit.HOURS);
	}

	public TimeSlotStat getCurrentMinuteStat() {
		return minuteCache.getIfPresent(currentMinuteKey());
	}

	public TimeSlotStat getCurrentTenMinStat() {
		return tenMinCache.getIfPresent(currentTenMinKey());
	}

	public TimeSlotStat getCurrentHourStat() {
		return hourCache.getIfPresent(currentHourKey());
	}

	private String timeKey(long ts, ChronoUnit unit) {
		LocalDateTime dt = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDateTime();
		if (unit == ChronoUnit.MINUTES) {
			return dt.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
		}
		if (unit == ChronoUnit.HOURS) {
			return dt.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
		}
		throw new IllegalArgumentException();
	}

	private String timeKey10Min(long ts) {
		LocalDateTime dt = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDateTime();
		int min = (dt.getMinute() / 10) * 10;
		return String.format("%s%02d", dt.format(DateTimeFormatter.ofPattern("yyyyMMddHH")), min);
	}
}
