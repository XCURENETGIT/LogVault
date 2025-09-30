package com.xcurenet.crypto.compress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

public class LZ4Compress implements CompressCodec {
	private final LZ4Factory factory = LZ4Factory.fastestInstance();
	private final LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();

	public void compress(final InputStream in, final OutputStream out) throws IOException {
		final LZ4Compressor lz4Compressor = factory.fastCompressor();
		int nread;
		final byte[] buf = new byte[4096];
		while ((nread = in.read(buf)) != -1) {
			out.write(lz4Compressor.compress(buf, 0, nread));
		}
	}

	public byte[] compress(final byte[] src) {
		return compress(src, 0, src.length);
	}

	public byte[] compress(final byte[] src, final int srcOff, final int srcLen) {
		return compressor.compress(src, srcOff, srcLen);
	}

	public void decompress(final InputStream in, final OutputStream out) throws IOException {
		final LZ4FastDecompressor decompressor = factory.fastDecompressor();
		int nread;
		final byte[] buf = new byte[4096];
		while ((nread = in.read(buf)) != -1) {
			out.write(decompressor.decompress(buf, 0, nread));
		}
	}
}
