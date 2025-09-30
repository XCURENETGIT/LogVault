package com.xcurenet.common.utils;

import java.util.HashMap;
import java.util.Map;

public class DynamicEnum {

	private static final Map<String, Enum<?>> dynamicEnums = new HashMap<>();

	public static <T extends Enum<T>> T addEnum(Class<T> clazz, String name) {
		// 이미 추가된 값이 있는지 체크
		if (!dynamicEnums.containsKey(name)) {
			try {
				T newEnum = Enum.valueOf(clazz, name);
				dynamicEnums.put(name, newEnum);  // 동적 값으로 추가
			} catch (IllegalArgumentException e) {
				System.out.println("Invalid Enum value: " + name);
				return null;
			}
		}
		return (T) dynamicEnums.get(name);
	}

	// 기존의 Enum 값을 가져오는 메서드
	public static <T extends Enum<T>> T getEnum(Class<T> clazz, String name) {
		if (dynamicEnums.containsKey(name)) {
			return (T) dynamicEnums.get(name);
		}
		return null;
	}
}
