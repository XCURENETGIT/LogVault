package com.xcurenet.logvault.module.statics;

import lombok.Data;

import java.util.concurrent.ConcurrentHashMap;

@Data
public class TimeSlotStat {
	private final StatCounter total = new StatCounter();
	private final ConcurrentHashMap<String, StatCounter> userMap = new ConcurrentHashMap<>();

	public void incPrompt(String userId) {
		total.incPrompt();
		userMap.computeIfAbsent(userId, k -> new StatCounter()).incPrompt();
	}

	public void incAttach(String userId) {
		total.incAttach();
		userMap.computeIfAbsent(userId, k -> new StatCounter()).incAttach();
	}
}
