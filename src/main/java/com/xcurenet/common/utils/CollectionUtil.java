package com.xcurenet.common.utils;

import com.google.common.cache.*;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class CollectionUtil {
	private CollectionUtil() {
		throw new IllegalStateException("Utility class");
	}

	public static boolean isEmpty(final Collection<?> collection) {
		return collection == null || collection.isEmpty();
	}

	public static boolean isNotEmpty(final Collection<?> collection) {
		return !isEmpty(collection);
	}

	public static <K, V> Cache<K, V> makeCache() {
		return CacheBuilder.newBuilder().build();
	}

	public static <K, V> LoadingCache<K, V> makeCache(final CacheLoader<K, V> loader) {
		return CacheBuilder.newBuilder().build(loader);
	}

	public static <K, V> Cache<K, V> makeCache(final long duration, final TimeUnit unit) {
		return CacheBuilder.newBuilder().expireAfterWrite(duration, unit).build();
	}

	public static <K, V> Cache<K, V> makeCache(final long duration, final TimeUnit unit, final RemovalListener<K, V> listener) {
		return CacheBuilder.newBuilder().expireAfterWrite(duration, unit).removalListener(listener).build();
	}

	public static <K, V> LoadingCache<K, V> makeCache(final CacheLoader<K, V> loader, final long duration, final TimeUnit unit, final RemovalListener<K, V> listener) {
		return CacheBuilder.newBuilder().expireAfterWrite(duration, unit).removalListener(listener).build(loader);
	}
}
