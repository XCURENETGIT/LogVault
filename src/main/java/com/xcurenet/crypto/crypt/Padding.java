package com.xcurenet.crypto.crypt;

import com.xcurenet.crypto.Crypto;

// PKCS#5/7 Padding
public class Padding {
	public static byte[] add(final byte[] input, final int inputOffset, final int inputLen) {
		final int remain = inputLen % Crypto.BLOCK_SIZE;
		if (remain == 0) {
			return null;
		}
		
		final int padSize = Crypto.BLOCK_SIZE - remain;
		final byte[] output = new byte[Crypto.BLOCK_SIZE];
		System.arraycopy(input, inputOffset, output, 0, inputLen);
		for (int i = inputLen; i < Crypto.BLOCK_SIZE; i++) {
			output[i] = (byte) padSize;
		}
		return output;
	}
	
	public static byte[] removePadding(final byte[] b) {
		final byte padLength = b[b.length - 1];
		final int length = Crypto.BLOCK_SIZE - (byte) padLength & 0xFF;
		if (length < 1) {
			return b;
		}
		
		for (int i = length; i < Crypto.BLOCK_SIZE; i++) {
			if (b[i] != padLength) {
				return b;
			}
		}
		
		final byte[] out = new byte[length];
		System.arraycopy(b, 0, out, 0, length); 
		return out;
	}
}
