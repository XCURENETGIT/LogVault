package com.xcurenet.crypto;

import com.xcurenet.common.utils.CommonUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class CryptoHeader {
	public static final int HEADER_SIZE = 16;
	private static final byte[] XCN = "XCN".getBytes();
	private static final byte RESERVE1 = 0x01;
	private static final byte RESERVE2 = 0x00;
	private static final byte VERSION = 0x02;

	public final byte reserve1 = RESERVE1;
	public byte version = VERSION;
	public byte encryptType;
	public byte compressType;
	public byte reserve2 = RESERVE2;
	public long contentLength;

	public boolean read(final InputStream in) throws IOException {
		final byte[] headerBuffer = new byte[HEADER_SIZE];
		if (in.read(headerBuffer) != HEADER_SIZE) {
			return false;
		}
		return read(headerBuffer);
	}

	public boolean read(final byte[] headerBuffer) {
		for (int i = 0; i < XCN.length; i++) {
			if (headerBuffer[i] != XCN[i]) {
				return false;
			}
		}
		if (headerBuffer[3] != RESERVE1) {
			return false;
		}
		version = headerBuffer[4];
		encryptType = headerBuffer[5];
		compressType = headerBuffer[6];
		reserve2 = headerBuffer[7];
		contentLength = toLong(headerBuffer, 8, 8);

		if (Arrays.binarySearch(Crypto.ENCRYPT_TYPE, encryptType) == -1) {
			return false;
		}

		return Arrays.binarySearch(Crypto.COMPRESS_TYPE, compressType) != -1;
	}

	public byte[] toBytes() throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream(HEADER_SIZE);
		out.write(XCN);
		out.write(reserve1);
		out.write(version);
		out.write(encryptType);
		out.write(compressType);
		out.write(reserve2);
		out.write(toBytes(contentLength, 8));
		return out.toByteArray();
	}

	public static byte[] toBytes(final long n, final int length) {
		return CommonUtil.toBytes(n, length);
	}

	public static long toLong(final byte[] input, final int inputOffset, final int inputLen) {
		long l = 0;
		for (int i = 0; i < inputLen; i++) {
			l <<= 8;
			l ^= input[inputOffset + i] & 0xFF;
		}
		return l;
	}

	public int getPadSize() {
		final int mod = (int) (contentLength % Crypto.BLOCK_SIZE);
		return mod == 0 ? 0 : Crypto.BLOCK_SIZE - mod;
	}

	public int getNumBlocks() {
		return (int) Math.ceil((double) contentLength / Crypto.BUFFER_SIZE);
	}

	public int getTrailerSize() {
		return version >= 2 ? CryptoTrailer.TRAILER_SIZE : CryptoTrailer.DIGEST_SIZE;
	}
}
