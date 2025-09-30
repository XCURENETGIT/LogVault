package com.xcurenet.crypto;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;

import org.apache.commons.io.IOUtils;

import com.xcurenet.crypto.compress.CompressCodec;
import com.xcurenet.crypto.compress.LZ4Compress;
import com.xcurenet.crypto.crypt.AESCrypt;
import com.xcurenet.crypto.crypt.AESCrypt.BlockCipherMode;
import com.xcurenet.crypto.crypt.ARIACrypt;
import com.xcurenet.crypto.crypt.CryptCodec;
import com.xcurenet.crypto.crypt.CryptCodec.CryptMode;

public class Crypto {
	public static final int BLOCK_SIZE = 16;
	public static final int BUFFER_SIZE = 4096;

	public static final String DIGEST_ALG = "SHA-256";

	private static final String ASCII = "ISO-8859-1";
	private static final byte[] XCNKEY = new byte[]{'7', 'T', 'Q', 'F', '1', 'N', '4', 'U', 'K', 'X', 'O', 'K', 'D', 'N', 'N', 'G'};
	private static final byte[] XCNKEY_FILED = "XCNKEY=".getBytes();
	private static final int KEYFILE_SIZE = 80;

	private byte[] key;
	private byte encryptType;
	private byte compressType;
	private CryptCodec encryptCodec;
	private CompressCodec compressCodec;
	private byte[] lastDigest;

	public static final int ARIA = 0x10;
	public static final int AES = 0x20;
	public static final int AES_ECB = 0x30;

	public static final int KEY128 = 0x01;
	public static final int KEY192 = 0x02;
	public static final int KEY256 = 0x03;

	public static final int UNKNOWN_DECRYPT_ONLY = 0;
	public static final int ARIA_128_CBC = ARIA | KEY128;
	public static final int ARIA_192_CBC = ARIA | KEY192;
	public static final int ARIA_256_CBC = ARIA | KEY256;
	public static final int AES_128_CBC = AES | KEY128;
	public static final int AES_192_CBC = AES | KEY192;
	public static final int AES_256_CBC = AES | KEY256;
	public static final int AES_128_ECB = AES_ECB | KEY128;
	public static final int AES_192_ECB = AES_ECB | KEY192;
	public static final int AES_256_ECB = AES_ECB | KEY256;

	public static final int[] ENCRYPT_TYPE = new int[]{ARIA_128_CBC, ARIA_192_CBC, ARIA_256_CBC, AES_128_CBC, AES_192_CBC, AES_256_CBC, AES_128_ECB, AES_192_ECB, AES_256_ECB};

	public enum CIPHER {
		ARIA_128_CBC(Crypto.ARIA_128_CBC), ARIA_192_CBC(Crypto.ARIA_192_CBC), ARIA_256_CBC(Crypto.ARIA_256_CBC), AES_128_CBC(Crypto.AES_128_CBC), AES_192_CBC(Crypto.AES_192_CBC), AES_256_CBC(Crypto.AES_256_CBC), AES_128_ECB(Crypto.AES_128_ECB), AES_192_ECB(Crypto.AES_192_ECB), AES_256_ECB(Crypto.AES_256_ECB),

		UNKNOWN(0);

		private int encryptType;

		CIPHER(final int encryptType) {
			this.encryptType = encryptType;
		}

		public int toEncryptType() {
			return this.encryptType;
		}

		public static CIPHER getCipher(final String cipher) {
			final String name = cipher.toUpperCase().replaceAll("-", "_");
			try {
				return CIPHER.valueOf(name);
			} catch (final Exception e) {
				return UNKNOWN;
			}
		}
	}

	public static final int COMPRESS_NONE = 0x00;
	public static final int COMPRESS_LZ4 = 0x01;
	public static final int[] COMPRESS_TYPE = new int[]{COMPRESS_NONE, COMPRESS_LZ4};

	public Crypto(final byte[] key) throws Exception {
		this(key, UNKNOWN_DECRYPT_ONLY);
	}

	public Crypto(final byte[] key, final CIPHER cipher) throws Exception {
		this(key, cipher.encryptType);
	}

	public Crypto(final byte[] key, final int encryptType) throws Exception {
		this(key, encryptType, COMPRESS_NONE);
	}

	public Crypto(final byte[] key, final int encryptType, final int compressType) throws Exception {
		setKey(key);
		setCryptCodec(encryptType);
		setCompressCodec(compressType);
	}

	public void setKey(final byte[] key) {
		this.key = key != null ? key.clone() : new byte[32];
	}

	public void setCryptCodec(final byte encryptType) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException {
		setCryptCodec(encryptType & 0xFF);
	}

	public void setCryptCodec(final int encryptType) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException {
		this.encryptType = (byte) encryptType;
		this.encryptCodec = getCryptCodec(encryptType);
	}

	private CryptCodec getCryptCodec(final int encryptType) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException {
		switch (encryptType) {
			case ARIA_128_CBC:
				return new ARIACrypt(128);
			case ARIA_192_CBC:
				return new ARIACrypt(192);
			case ARIA_256_CBC:
				return new ARIACrypt(256);
			case AES_128_CBC:
				return new AESCrypt(BlockCipherMode.CBC, 128);
			case AES_192_CBC:
				return new AESCrypt(BlockCipherMode.CBC, 192);
			case AES_256_CBC:
				return new AESCrypt(BlockCipherMode.CBC, 256);
			case AES_128_ECB:
				return new AESCrypt(BlockCipherMode.ECB, 128);
			case AES_192_ECB:
				return new AESCrypt(BlockCipherMode.ECB, 192);
			case AES_256_ECB:
				return new AESCrypt(BlockCipherMode.ECB, 256);
			case UNKNOWN_DECRYPT_ONLY:
				return null;
			default:
				throw new NoSuchAlgorithmException("Not support algorithm");
		}
	}

	public void setCompressCodec(final byte compressType) throws NoSuchAlgorithmException {
		setCompressCodec(compressType & 0xFF);
	}

	public void setCompressCodec(final int compressType) throws NoSuchAlgorithmException {
		this.compressType = (byte) compressType;
		this.compressCodec = getCompressCodec(compressType);
	}

	private CompressCodec getCompressCodec(final int compressType) throws NoSuchAlgorithmException {
		switch (compressType) {
			case COMPRESS_NONE:
				return null;
			case COMPRESS_LZ4:
				return new LZ4Compress();
			default:
				throw new NoSuchAlgorithmException("Not support algorithm");
		}
	}

	public void init(final CryptMode mode) throws InvalidKeyException, InvalidAlgorithmParameterException {
		encryptCodec.init(mode, key);
	}

	public byte[] encrypt(final byte[] src, final int offset, final int length) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, ShortBufferException, NoSuchAlgorithmException, IOException {
		final ByteArrayInputStream in = new ByteArrayInputStream(src, offset, length);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		encrypt(in, out, length);
		return out.toByteArray();
	}

	public byte[] encrypt(final InputStream in, final OutputStream out, final long len) throws IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, ShortBufferException, NoSuchAlgorithmException {
		if (encryptCodec == null) {
			throw new NoSuchAlgorithmException("Algorithm not defined");
		}
		final InputStream input = in instanceof BufferedInputStream ? in : new BufferedInputStream(in);

		init(CryptMode.ENCRYPT_MODE);
		final MessageDigest digest = MessageDigest.getInstance(DIGEST_ALG);

		out.write(getHeader(len).toBytes());

		int writeLength = 0;
		int nread = -1;
		final byte[] inputBuf = new byte[BUFFER_SIZE];
		final byte[] outputBuf = new byte[BUFFER_SIZE];
		while ((nread = input.read(inputBuf)) > 0) {
			digest.update(inputBuf, 0, nread);
			final int dstLen = update(inputBuf, 0, nread, outputBuf, 0);
			out.write(outputBuf, 0, dstLen);
			writeLength += nread;
		}
		out.write(doFinal());

		final byte[] dgst = digest.digest();
		out.write(new CryptoTrailer(dgst, writeLength).toBytes());
		return dgst;
	}

	public byte[] decrypt(final byte[] src, final int offset, final int length) throws Exception {
		final ByteArrayInputStream in = new ByteArrayInputStream(src, offset, length);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (decrypt(in, out)) {
			return out.toByteArray();
		} else {
			return null;
		}
	}

	public boolean decrypt(final InputStream in, final OutputStream out) throws Exception {
		final InputStream input = in instanceof BufferedInputStream ? in : new BufferedInputStream(in);

		final CryptoHeader header = new CryptoHeader();
		if (!header.read(input)) {
			System.out.println("header cant not read");
			return false;
		}

		setCryptCodec(header.encryptType);
		setCompressCodec(header.compressType);
		init(CryptMode.DECRYPT_MODE);

		final MessageDigest digest = MessageDigest.getInstance(DIGEST_ALG);

		final int padSize = header.getPadSize();
		final int numBlock = header.getNumBlocks();

		final byte[] inputBuf = new byte[BUFFER_SIZE];
		final byte[] outputBuf = new byte[BUFFER_SIZE];

		for (int i = numBlock - 1; i >= 0; i--) {
			final boolean lastBlock = i == 0;
			int nread = BUFFER_SIZE;
			if (lastBlock) {
				nread = (int) (header.contentLength % BUFFER_SIZE) + padSize;
			}
			nread = input.read(inputBuf, 0, nread);
			if (nread <= 0) {
				break;
			}

			update(inputBuf, 0, nread, outputBuf, 0);
			if (lastBlock) {
				nread -= padSize;
			}
			if (out != null) {
				out.write(outputBuf, 0, nread);
			}
			digest.update(outputBuf, 0, nread);
		}
		if (out != null) {
			out.write(doFinal());
		}

		final byte[] hash = new byte[32];
		input.read(hash);
		lastDigest = digest.digest();
		return Arrays.equals(hash, lastDigest);
	}

	public byte[] update(final byte[] input) throws InvalidKeyException {
		return encryptCodec.update(input, 0, input.length);
	}

	public int update(final byte[] input, final int inputOffset, final int inputLen, final byte[] output, final int outputOffset) throws InvalidKeyException, ShortBufferException {
		final int numBlock = (int) Math.ceil((double) inputLen / BLOCK_SIZE);
		for (int i = 0; i < numBlock; i++) {
			final int pos = i * BLOCK_SIZE;
			final int offset = inputOffset + pos;
			final int len = inputLen - offset < BLOCK_SIZE ? inputLen - offset : BLOCK_SIZE;
			encryptCodec.update(input, offset, len, output, outputOffset + pos);
		}
		return numBlock * BLOCK_SIZE;
	}

	public byte[] doFinal() throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		return encryptCodec.doFinal();
	}

	public byte[] doFinal(final byte[] input) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		return doFinal(input, 0, input.length);
	}

	public int doFinal(final byte[] output, final int outputOffset) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
		return doFinal(null, 0, 0, output, outputOffset);
	}

	public byte[] doFinal(final byte[] input, final int inputOffset, final int inputLen) throws IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
		return encryptCodec.doFinal(input, inputOffset, inputLen);
	}

	public int doFinal(final byte[] input, final int inputOffset, final int inputLen, final byte[] output) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
		return doFinal(input, inputOffset, inputLen, output, 0);
	}

	public int doFinal(final byte[] input, final int inputOffset, final int inputLen, final byte[] output, final int outputOffset) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
		return encryptCodec.doFinal(input, inputOffset, inputLen, output, outputOffset);
	}

	public CryptoHeader getHeader(final long length) throws IOException {
		final CryptoHeader header = new CryptoHeader();
		header.encryptType = encryptType;
		header.compressType = compressType;
		header.contentLength = length;
		return header;
	}

	public static boolean isEncryptedFile(final File file) throws IOException {
		if (file.length() < CryptoHeader.HEADER_SIZE + CryptoTrailer.DIGEST_SIZE) {
			return false;
		}
		FileInputStream in = null;
		try {
			in = new FileInputStream(file);
			return isEncryptedFile(in);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	public static boolean isEncryptedFile(final InputStream in) throws IOException {
		return new CryptoHeader().read(in);
	}

	public static byte[] makeIntegrityHash(final byte[] key, final File file) throws Exception {
		InputStream in = null;
		try {
			in = new FileInputStream(file);
			return makeIntegrityHash(key, in);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	public static byte[] makeIntegrityHash(final byte[] key, InputStream in) throws Exception {
		in = new CryptoInputStream(new Crypto(key), in);
		final MessageDigest digest = MessageDigest.getInstance(DIGEST_ALG);
		final byte[] buf = new byte[BUFFER_SIZE];
		int nread = 0;
		while ((nread = in.read(buf)) != -1) {
			digest.update(buf, 0, nread);
		}
		return digest.digest();
	}

	public static boolean isEncryptedFile(final byte[] buf) {
		if (buf == null || buf.length < CryptoHeader.HEADER_SIZE) {
			return false;
		}

		final CryptoHeader header = new CryptoHeader();
		return header.read(buf);
	}

	public static boolean makeKeyFile(final String keyfile, final String password) {
		FileOutputStream out = null;
		try {
			final MessageDigest digest = MessageDigest.getInstance(DIGEST_ALG);
			digest.update(password.getBytes(ASCII));
			final byte[] key = digest.digest();

			final ByteArrayOutputStream baos = new ByteArrayOutputStream(16);
			baos.write(XCNKEY_FILED);
			baos.write(key);
			final byte[] input = baos.toByteArray();
			final int outputLen = (int) (Math.ceil((double) input.length / BLOCK_SIZE) * BLOCK_SIZE);
			final byte[] output = new byte[outputLen];
			final Crypto crypto = new Crypto(XCNKEY, Crypto.ARIA_128_CBC);
			crypto.init(CryptMode.ENCRYPT_MODE);
			crypto.update(input, 0, input.length, output, 0);

			out = new FileOutputStream(keyfile);
			out.write(output);
			digest.reset();
			out.write(digest.digest(input));
			return true;
		} catch (final Exception e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(out);
		}
		return false;
	}

	public static byte[] loadKeyFile(final String keyfile) {
		InputStream in = null;
		final File file = new File(keyfile);
		if (file.exists() && file.length() == KEYFILE_SIZE) {
			try {
				in = new FileInputStream(file);
				final byte[] buf = new byte[KEYFILE_SIZE];
				final byte[] out = new byte[KEYFILE_SIZE];
				if (in.read(buf) == KEYFILE_SIZE) {
					final Crypto crypto = new Crypto(XCNKEY, Crypto.ARIA_128_CBC);
					crypto.init(CryptMode.DECRYPT_MODE);
					crypto.update(buf, 0, buf.length - CryptoTrailer.DIGEST_SIZE, out, 0);

					// 키필드 검사
					for (int i = 0; i < XCNKEY_FILED.length; i++) {
						if (XCNKEY_FILED[i] != out[i]) {
							return null;
						}
					}

					// 무결성 검사
					final MessageDigest digest = MessageDigest.getInstance(DIGEST_ALG);
					digest.update(out, 0, XCNKEY_FILED.length + CryptoTrailer.DIGEST_SIZE);
					final byte[] hash = digest.digest();
					for (int i = 0; i < CryptoTrailer.DIGEST_SIZE; i++) {
						if (hash[i] != buf[KEYFILE_SIZE - CryptoTrailer.DIGEST_SIZE + i]) {
							return null;
						}
					}

					final byte[] key = new byte[CryptoTrailer.DIGEST_SIZE];
					System.arraycopy(out, XCNKEY_FILED.length, key, 0, CryptoTrailer.DIGEST_SIZE);
					return key;
				}
			} catch (final Exception e) {
				e.printStackTrace();
			} finally {
				IOUtils.closeQuietly(in);
			}
		}
		return null;
	}
}
