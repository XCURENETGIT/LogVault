package com.xcurenet.crypto;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class GrowBufferdOutputStream extends FilterOutputStream {
	private static final int DEFAULT_BUFFER_SIZE = 8192;
	private static final int MAX_BUFFER_SIZE = 2147483639;

	protected byte[] buf;
	protected int count = 0;

	public GrowBufferdOutputStream(final OutputStream out) {
		this(out, DEFAULT_BUFFER_SIZE);
	}

	public GrowBufferdOutputStream(final OutputStream out, final int bufferSize) {
		super(out);
		this.buf = new byte[bufferSize];
	}

	@Override
	public synchronized void write(final int paramInt) throws IOException {
		if (count >= buf.length) {
			flushBuffer();
		}
		buf[count++] = (byte) paramInt;
	}

	@Override
	public synchronized void write(final byte[] src, final int offset, final int length) throws IOException {
		if (length > buf.length) {
			// 버퍼 사이즈보다 큰 사이즈의 데이터가 들어올 경우 버퍼 증설
			flushBuffer();
			grow(length);
			write(src, offset, length);
			return;
		}

		if (length > buf.length - count) {
			flushBuffer();
		}
		System.arraycopy(src, offset, buf, count, length);
		count += length;
	}

	@Override
	public synchronized void flush() throws IOException {
		flushBuffer();
		// 상황에 따라 OutputStream 없이 생성할 수 있음.
		if (out != null) {
			super.flush();
		}
	}

	// 버퍼가 부족한 경우 2배씩 증가시킴
	private void grow(final int length) {
		if (length > MAX_BUFFER_SIZE) {
			throw new OutOfMemoryError();
		}

		int newLength = 0;
		while (newLength < length) {
			newLength = buf.length * 2;
		}
		if (newLength > MAX_BUFFER_SIZE) {
			newLength = MAX_BUFFER_SIZE;
		}
		buf = new byte[newLength];
	}

	protected void flushBuffer() throws IOException {
		if (count <= 0) {
			return;
		}
		super.write(buf, 0, count);
		count = 0;
	}
}
