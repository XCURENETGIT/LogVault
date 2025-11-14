package com.xcurenet.common.utils;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.logvault.exception.LogVaultException;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class ExFactory {

	private ExFactory() {
	}

	public static <E extends LogVaultException> E ex(BiFunction<ErrorCode, Throwable, E> ctor, ErrorCode ec, Map<String, Object> ctx, Throwable cause) {
		E e = ctor.apply(ec, cause);
		if (ctx != null) e.addAll(ctx);
		e.print();
		return e;
	}

	public static <E extends LogVaultException> E ex(Function<ErrorCode, E> ctor, ErrorCode ec, Map<String, Object> ctx) {
		E e = ctor.apply(ec);
		if (ctx != null) e.addAll(ctx);
		e.print();
		return e;
	}
}