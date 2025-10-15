package com.xcurenet.common.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;

public class LimitedBufferedReader extends Reader {

	private static final int DEFAULT_MAX_LINE_LENGTH = 8192;
	private static final int defaultCharBufferSize = 8192;

	private final int lineLength;

	private Reader in;

	private char[] cb;
	private int nChars, nextChar;

	private static final int INVALIDATED = -2;
	private static final int UNMARKED = -1;
	private int markedChar = UNMARKED;
	private int readAheadLimit = 0; /* Valid only when markedChar > 0 */

	/**
	 * If the next character is a line feed, skip it
	 */
	private boolean skipLF = false;

	public LimitedBufferedReader(Reader in, int sz, int linelength) {
		super(in);
		if (sz <= 0) throw new IllegalArgumentException("Buffer size <= 0");

		if (linelength <= 0) throw new IllegalArgumentException("Line Length size <= 0");

		this.in = in;
		this.cb = new char[sz];
		this.nextChar = 0;
		this.nChars = 0;
		this.lineLength = linelength;
	}

	public LimitedBufferedReader(Reader in, int sz) {
		this(in, sz, DEFAULT_MAX_LINE_LENGTH);
	}

	public LimitedBufferedReader(Reader in) {
		this(in, defaultCharBufferSize, DEFAULT_MAX_LINE_LENGTH);
	}

	/**
	 * Checks to make sure that the stream has not been closed
	 */
	private void ensureOpen() throws IOException {
		if (in == null) throw new IOException("Stream closed");
	}

	private void fill() throws IOException {
		int dst;
		if (markedChar <= UNMARKED) {
			/* No mark */
			dst = 0;
		} else {
			/* Marked */
			int delta = nextChar - markedChar;
			if (delta >= readAheadLimit) {
				/* Gone past read-ahead limit: Invalidate mark */
				markedChar = INVALIDATED;
				readAheadLimit = 0;
				dst = 0;
			} else {
				if (readAheadLimit <= cb.length) {
					/* Shuffle in the current buffer */
					System.arraycopy(cb, markedChar, cb, 0, delta);
				} else {
					/* Reallocate buffer to accommodate read-ahead limit */
					char[] ncb = new char[readAheadLimit];
					System.arraycopy(cb, markedChar, ncb, 0, delta);
					cb = ncb;
				}
				markedChar = 0;
				dst = delta;
				nextChar = nChars = delta;
			}
		}

		int n;
		do {
			n = in.read(cb, dst, cb.length - dst);
		} while (n == 0);
		if (n > 0) {
			nChars = dst + n;
			nextChar = dst;
		}
	}

	@Override
	public int read() throws IOException {
		synchronized (lock) {
			ensureOpen();
			for (; ; ) {
				if (nextChar >= nChars) {
					fill();
					if (nextChar >= nChars) return -1;
				}
				if (skipLF) {
					skipLF = false;
					if (cb[nextChar] == '\n') {
						nextChar++;
						continue;
					}
				}
				return cb[nextChar++];
			}
		}
	}

	private int read1(char[] cbuf, int off, int len) throws IOException {
		if (nextChar >= nChars) {

			if (len >= cb.length && markedChar <= UNMARKED && !skipLF) {
				return in.read(cbuf, off, len);
			}
			fill();
		}
		if (nextChar >= nChars) return -1;
		if (skipLF) {
			skipLF = false;
			if (cb[nextChar] == '\n') {
				nextChar++;
				if (nextChar >= nChars) fill();
				if (nextChar >= nChars) return -1;
			}
		}
		int n = Math.min(len, nChars - nextChar);
		System.arraycopy(cb, nextChar, cbuf, off, n);
		nextChar += n;
		return n;
	}

	@Override
	public int read(@NotNull char[] cbuf, int off, int len) throws IOException {
		synchronized (lock) {
			ensureOpen();
			if ((off < 0) || (off > cbuf.length) || (len < 0) || ((off + len) > cbuf.length) || ((off + len) < 0)) {
				throw new IndexOutOfBoundsException();
			} else if (len == 0) {
				return 0;
			}

			int n = read1(cbuf, off, len);
			if (n <= 0) return n;
			while ((n < len) && in.ready()) {
				int n1 = read1(cbuf, off + n, len - n);
				if (n1 <= 0) break;
				n += n1;
			}
			return n;
		}
	}

	public String readLine(boolean ignoreLF) throws IOException {
		StringBuilder s = null;
		int startChar;

		synchronized (lock) {
			ensureOpen();
			boolean omitLF = ignoreLF || skipLF;

			for (; ; ) {
				if (nextChar >= nChars) fill();
				if (nextChar >= nChars) { /* EOF */
					if (s != null && !s.isEmpty()) return s.toString();
					else return null;
				}
				boolean eol = false;
				char c = 0;
				int i;

				/* Skip a leftover '\n', if necessary */
				if (omitLF && (cb[nextChar] == '\n')) nextChar++;
				skipLF = false;
				omitLF = false;

				for (i = nextChar; i < nChars; i++) {
					c = cb[i];
					if ((c == '\n') || (c == '\r') || (s != null && s.length() + i >= lineLength)) {
						eol = true;
						break;
					}
				}

				startChar = nextChar;
				nextChar = i;

				if (eol) {
					String str;
					if (s == null) {
						str = new String(cb, startChar, i - startChar);
					} else {
						s.append(cb, startChar, i - startChar);
						str = s.toString();
					}
					nextChar++;
					if (c == '\r') {
						skipLF = true;
					}
					return str;
				}

				int defaultExpectedLineLength = 80;
				if (s == null) s = new StringBuilder(defaultExpectedLineLength);

				s.append(cb, startChar, i - startChar);
			}
		}
	}

	public String readLine() throws IOException {
		return readLine(false);
	}

	@Override
	public boolean ready() throws IOException {
		synchronized (lock) {
			ensureOpen();

			if (skipLF) {

				if (nextChar >= nChars && in.ready()) {
					fill();
				}
				if (nextChar < nChars) {
					if (cb[nextChar] == '\n') nextChar++;
					skipLF = false;
				}
			}
			return (nextChar < nChars) || in.ready();
		}
	}

	@Override
	public void close() throws IOException {
		synchronized (lock) {
			if (in == null) return;
			try {
				in.close();
			} finally {
				in = null;
				cb = null;
			}
		}
	}
}
