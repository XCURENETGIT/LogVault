package com.xcurenet.common.io;

import org.jetbrains.annotations.NotNull;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncOutputStream extends FilterOutputStream {
	private static final int DEFAULT_CAPACITY = 1024;
	private final BlockingQueue<byte[]> queue;
	private final AtomicBoolean close = new AtomicBoolean(false);
	private final CountDownLatch latch = new CountDownLatch(1);

	public AsyncOutputStream(final OutputStream out) {
		this(out, DEFAULT_CAPACITY);
	}

	public AsyncOutputStream(final OutputStream out, final int capacity) {
		super(out);
		this.queue = new ArrayBlockingQueue<byte[]>(capacity);
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (!close.get() || !queue.isEmpty()) {
						final byte[] b = queue.poll(1, TimeUnit.MILLISECONDS);
						if (b == null) {
							continue;
						}
						out.write(b, 0, b.length);
					}
				} catch (final Exception e) {
					throw new RuntimeException(e);
				} finally {
					latch.countDown();
				}
			}
		});
		thread.start();
	}

	@Override
	public synchronized void write(final int paramInt) throws IOException {
		try {
			queue.put(new byte[]{(byte) paramInt});
		} catch (final InterruptedException e) {
			throw new IOException(e);
		}
	}

	@Override
	public synchronized void write(@NotNull final byte[] buf, final int offset, final int length) throws IOException {
		try {
			queue.put(Arrays.copyOfRange(buf, offset, offset + length));
		} catch (final InterruptedException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void close() throws IOException {
		try {
			close.set(true);
			latch.await();
		} catch (final InterruptedException e) {
			throw new IOException(e);
		} finally {
			super.close();
		}
	}
}
