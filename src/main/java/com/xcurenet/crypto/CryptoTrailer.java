package com.xcurenet.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public record CryptoTrailer(byte[] digest, byte padSize) {
	public static final int DIGEST_SIZE = 32;
	public static final int TRAILER_SIZE = DIGEST_SIZE + 1;

	public CryptoTrailer(final byte[] digest, final long contentLength) {
		this(digest, getPadSize(contentLength));
	}

	private static byte getPadSize(final long contentLength) {
		final int remain = (int) (contentLength % Crypto.BLOCK_SIZE);
		return (byte) (remain != 0 ? Crypto.BLOCK_SIZE - remain : 0);
	}

	public static CryptoTrailer read(final byte[] buf, final int offset) {
		final byte[] digest = Arrays.copyOfRange(buf, offset, offset + DIGEST_SIZE);
		final byte padSize = buf[offset + DIGEST_SIZE];
		return new CryptoTrailer(digest, padSize);
	}

	public static CryptoTrailer read(final byte[] buf, final int offset, final int len, final InputStream in) throws IOException {
		final byte[] trailerBuf = new byte[CryptoTrailer.TRAILER_SIZE];
		System.arraycopy(buf, offset, trailerBuf, 0, len);
		in.read(trailerBuf, len, trailerBuf.length - len);
		return read(trailerBuf, 0);
	}

	public byte[] toBytes() throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream(TRAILER_SIZE);
		out.write(digest);
		out.write(padSize);
		return out.toByteArray();
	}
}
