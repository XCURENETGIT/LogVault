package com.xcurenet.crypto;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;

import javax.crypto.ShortBufferException;

import com.xcurenet.crypto.crypt.CryptCodec.CryptMode;

public class CryptoInputStream extends FilterInputStream {
	private final Crypto crypto;
	private boolean encryptedFile = true;

	private final byte[] input = new byte[Crypto.BUFFER_SIZE];
	private final byte[] output = new byte[Crypto.BUFFER_SIZE];
	protected int initReadSize;

	private int trailerSize;
	private long contentLength;
	private int numBlocks;
	private int padSize;

	protected int offset;

	protected long blockCount;
	private boolean lastBlock;
	private int nread;
	private int version;

	public CryptoInputStream(final Crypto crypto, final InputStream in) throws IOException {
		super(in instanceof BufferedInputStream ? in : new BufferedInputStream(in));
		try {
			this.crypto = crypto;
			this.initReadSize = super.read(output, 0, CryptoHeader.HEADER_SIZE);
			if (this.initReadSize >= CryptoHeader.HEADER_SIZE) {
				final CryptoHeader header = new CryptoHeader();
				if (header.read(output)) {
					this.crypto.setCryptCodec(header.encryptType);
					this.crypto.setCompressCodec(header.compressType);
					this.crypto.init(CryptMode.DECRYPT_MODE);
					this.version = header.version;
					this.trailerSize = header.getTrailerSize();
					this.contentLength = header.contentLength;
					this.numBlocks = header.getNumBlocks();
					this.padSize = header.getPadSize();
					this.encryptedFile = true;
					return;
				}
			}
			this.encryptedFile = false;
		} catch (final Exception e) {
			throw new IOException(e);
		}
	}

	private int decode() throws InvalidKeyException, IOException, ShortBufferException {
		final int nTryRead = getTryRead();
		nread = super.read(input, 0, nTryRead);
		if (version >= 2) {
			// 버전2에서는 컨텐츠 길이는 압축적의 원본 사이즈이므로, 암호화 파일 끝의 패딩 사이즈를 찾는다.
			checkEOF(nTryRead);
		}

		if (nread > 0) {
			crypto.update(input, 0, nread, output, 0);
		}
		return nread;
	}

	private int getTryRead() {
		if (version == 1) {
			// 버전1에서는 컨텐츠 길이에서 패딩 사이즈를 계산한다.
			lastBlock = (++blockCount == numBlocks);
			if (lastBlock && padSize > 0) {
				return (int) ((contentLength % Crypto.BUFFER_SIZE) + padSize);
			}
		}
		return Crypto.BUFFER_SIZE;
	}

	private void checkEOF(final int nTryRead) throws IOException {
		if (nread < nTryRead) {
			nread -= trailerSize;
			nread -= CryptoTrailer.read(input, nread).padSize;
		} else {
			final int remain = available();
			if (remain <= trailerSize) {
				final int len = trailerSize - remain;
				nread -= len;
				nread -= CryptoTrailer.read(input, nread, len, in).padSize;
			}
		}
	}

	@Override
	public int read() throws IOException {
		if (!encryptedFile) {
			if (offset < initReadSize) {
				return output[offset++] & 0xFF;
			} else {
				return super.read();
			}
		}

		offset %= Crypto.BUFFER_SIZE;
		if (offset == 0) {
			try {
				if (decode() == -1) {
					return -1;
				}
			} catch (final Exception e) {
//				throw new IOException(e);
			}
		}
		if (offset >= nread) {
			return -1;
		}

		return output[offset++] & 0xFF;
	}

	@Override
	public int read(final byte[] b, final int off, final int len) throws IOException {
		if (!encryptedFile && offset >= initReadSize) {
			return super.read(b, off, len);
		}

		int i;
		for (i = 0; i < len; i++) {
			final int ret = read();
			if (ret == -1) {
				return i == 0 ? -1 : i;
			}
			b[off + i] = (byte) ret;
		}
		return i;
	}

	public long getLength() {
		return encryptedFile ? contentLength : -1;
	}

	public boolean isEncryptedFile() {
		return encryptedFile;
	}
}
