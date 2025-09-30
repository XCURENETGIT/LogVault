package com.xcurenet.crypto;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;

import com.xcurenet.crypto.crypt.CryptCodec.CryptMode;

public class CryptoOutputStream extends GrowBufferdOutputStream {
	private final Crypto crypto;
	private byte[] outBuf;
	private final MessageDigest digest;
	private long contentLength;

	public CryptoOutputStream(final Crypto crypto, final OutputStream out) throws IOException {
		this(crypto, out, 0);
	}

	public CryptoOutputStream(final Crypto crypto, final OutputStream out, final long length) throws IOException {
		super(out);
		this.outBuf = new byte[buf.length];
		this.crypto = crypto;
		try {
			this.crypto.init(CryptMode.ENCRYPT_MODE);
			this.digest = MessageDigest.getInstance(Crypto.DIGEST_ALG);
		} catch (final Exception e) {
			throw new IOException(e);
		}
		out.write(crypto.getHeader(length).toBytes());
	}

	@Override
	protected void flushBuffer() throws IOException {
		flushBuffer(false);
	}

	private void flushBuffer(final boolean flushAll) throws IOException {
		if (count <= 0) {
			return;
		}
		try {
			if (buf.length > outBuf.length) {
				outBuf = new byte[buf.length];
			}

			// 암호화 블럭 사이즈 단위로 flush 한다. force가 활성화된 경우 전부 flush.
			final int remain = flushAll ? 0 : count % Crypto.BLOCK_SIZE;
			if (remain > 0) {
				count -= remain;
			}
			contentLength += count;

			final int nWrite = crypto.update(buf, 0, count, outBuf, 0);
			out.write(outBuf, 0, nWrite);
			digest.update(buf, 0, count);

			if (remain > 0) {
				System.arraycopy(buf, count, buf, 0, remain);
			}
			count = remain;
		} catch (final Exception e) {
			throw new IOException(e);
		}
	}

	@Override
	public void close() throws IOException {
		// 남아있는 모든 버퍼를 강제로 flush. (블럭 단위 사이즈가 아닌 경우 패딩 생성)
		flushBuffer(true);
		out.write(new CryptoTrailer(digest.digest(), contentLength).toBytes());
		super.close();
	}
}
