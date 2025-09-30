package com.xcurenet.common.utils;

public class BaseSystemUtils {
	/**
	 * IO 핸들링 템플릿 메소드.
	 *
	 * @param io IOCallback<T>
	 * @return <T>
	 */
	public static <T> T processIO(IOCallback<T> io) {
		try {
			return io.doInProcessIO();
		} catch (Exception e) {
			throw new RuntimeException("processIO IOException occurred : " + e.getMessage(), e);
		}
	}

	/**
	 * IO callback interface
	 *
	 * @param <T>
	 * @author 임 성천.
	 */
	public interface IOCallback<T> {
		public T doInProcessIO() throws Exception;
	}
}
