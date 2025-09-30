package com.xcurenet.crypto.crypt;

import java.security.InvalidKeyException;

public class ARIACrypt implements CryptCodec {
	private static final int ARIA_BLOCK_SIZE = 16;
	private final ARIAEngine engine;
	private CryptMode mode;

	public ARIACrypt(final int keySize) throws InvalidKeyException {
		this.engine = new ARIAEngine(keySize);
	}

	@Override
	public void init(final CryptMode mode, final byte[] key) throws InvalidKeyException {
		this.mode = mode;
		this.engine.setKey(key);
		this.engine.setupRoundKeys();
	}

	@Override
	public byte[] update(final byte[] input, final int inputOffset, final int inputLen) throws InvalidKeyException {
		switch (mode) {
		case ENCRYPT_MODE:
			if (inputLen < ARIA_BLOCK_SIZE) {
				return engine.encrypt(Padding.add(input, inputOffset, inputLen), 0);
			} else {
				return engine.encrypt(input, inputOffset);
			}
		case DECRYPT_MODE:
			return engine.decrypt(input, 0);
		}
		return new byte[0];
	}

	@Override
	public int update(final byte[] input, final int inputOffset, final int inputLen, final byte[] output, final int outputOffset) throws InvalidKeyException {
		switch (mode) {
		case ENCRYPT_MODE:
			if (inputLen < ARIA_BLOCK_SIZE) {
				engine.encrypt(Padding.add(input, inputOffset, inputLen), 0, output, outputOffset);
			} else {
				engine.encrypt(input, inputOffset, output, outputOffset);
			}
			break;
		case DECRYPT_MODE:
			engine.decrypt(input, inputOffset, output, outputOffset);
			break;
		}
		return ARIA_BLOCK_SIZE;
	}

	@Override
	public byte[] doFinal() {
		return new byte[0];
	}

	@Override
	public byte[] doFinal(final byte[] input, final int inputOffset, final int inputLen) throws InvalidKeyException {
		return update(input, inputOffset, inputLen);
	}

	@Override
	public int doFinal(final byte[] input, final int inputOffset, final int inputLen, final byte[] output, final int outputOffset) throws InvalidKeyException {
		return update(input, inputOffset, inputLen, output, outputOffset);
	}
}
