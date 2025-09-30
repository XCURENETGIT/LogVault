package com.xcurenet.crypto.crypt;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;

public interface CryptCodec {
	enum CryptMode {
		ENCRYPT_MODE,
		DECRYPT_MODE
	}
	
	void init(final CryptMode mode, final byte[] key) throws InvalidKeyException, InvalidAlgorithmParameterException;
	
	byte[] update(final byte[] input, final int inputOffset, final int inputLen) throws InvalidKeyException;
	int update(final byte[] input, final int inputOffset, final int inputLen, final byte[] output, final int outputLen) throws InvalidKeyException, ShortBufferException;

	byte[] doFinal() throws IllegalBlockSizeException, BadPaddingException;
	byte[] doFinal(final byte[] input, final int inputOffset, final int inputLen) throws IllegalBlockSizeException, BadPaddingException, InvalidKeyException;
	int doFinal(final byte[] input, final int inputOffset, final int inputLen, final byte[] output, final int outputOffset) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException;
}
