package com.xcurenet.crypto.crypt;

import lombok.Getter;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AESCrypt implements CryptCodec {
	@Getter
	public enum BlockCipherMode {
		CBC("AES/CBC/NoPadding"), ECB("AES/ECB/NoPadding");

		private final String transformation;

		BlockCipherMode(final String transformation) {
			this.transformation = transformation;
		}

	}

	private static final int AES_BLOCK_SIZE = 16;
	private static final IvParameterSpec IVSPEC = new IvParameterSpec(new byte[AES_BLOCK_SIZE]);
	private final Cipher cipher;
	private final int keySize;
	private final BlockCipherMode bcm;

	public AESCrypt(final BlockCipherMode bcm, final int keySize) throws NoSuchAlgorithmException, NoSuchPaddingException {
		this.bcm = bcm;
		this.cipher = Cipher.getInstance(bcm.getTransformation());
		this.keySize = keySize;
	}

	@Override
	public void init(final CryptMode mode, final byte[] key) throws InvalidKeyException, InvalidAlgorithmParameterException {
		final int opmode = CryptMode.ENCRYPT_MODE.equals(mode) ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE;
		final SecretKeySpec keySpec = new SecretKeySpec(key, 0, keySize / 8, "AES");
		if (BlockCipherMode.CBC.equals(bcm)) {
			cipher.init(opmode, keySpec, IVSPEC);
		} else {
			cipher.init(opmode, keySpec);
		}
	}

	@Override
	public byte[] update(final byte[] input, final int inputOffset, final int inputLen) {
		if (inputLen < AES_BLOCK_SIZE) {
			return cipher.update(Objects.requireNonNull(Padding.add(input, inputOffset, inputLen)));
		} else {
			return cipher.update(input, inputOffset, inputLen);
		}
	}

	@Override
	public int update(final byte[] input, final int inputOffset, final int inputLen, final byte[] output, final int outputOffset) throws ShortBufferException {
		if (inputLen < AES_BLOCK_SIZE) {
			return cipher.update(Objects.requireNonNull(Padding.add(input, inputOffset, inputLen)), 0, AES_BLOCK_SIZE, output, outputOffset);
		} else {
			return cipher.update(input, inputOffset, inputLen, output, outputOffset);
		}
	}

	@Override
	public byte[] doFinal() throws IllegalBlockSizeException, BadPaddingException {
		return cipher.doFinal();
	}

	@Override
	public byte[] doFinal(final byte[] input, final int inputOffset, final int inputLen) throws IllegalBlockSizeException, BadPaddingException {
		return cipher.doFinal(input, inputOffset, inputLen);
	}

	@Override
	public int doFinal(final byte[] input, final int inputOffset, final int inputLen, final byte[] output, final int outputOffset) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
		return cipher.doFinal(input, inputOffset, inputLen, output, outputOffset);
	}
}
