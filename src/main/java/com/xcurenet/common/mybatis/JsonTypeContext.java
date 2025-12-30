package com.xcurenet.common.mybatis;

import com.fasterxml.jackson.core.type.TypeReference;

public class JsonTypeContext {

	private static final ThreadLocal<TypeReference<?>> TYPE_REF = new ThreadLocal<>();

	public static void set(TypeReference<?> typeReference) {
		TYPE_REF.set(typeReference);
	}

	public static TypeReference<?> get() {
		return TYPE_REF.get();
	}

	public static void clear() {
		TYPE_REF.remove();
	}
}
