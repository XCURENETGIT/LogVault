package com.xcurenet.common.ahocorasick;

/**
 * Represents an EdgeList by using a single array. Very fast lookup (just an
 * array access), but expensive in terms of memory.
 */

class DenseEdgeList implements EdgeList {
	private final State[] array;

	public DenseEdgeList() {
		this.array = new State[256];
		for (int i = 0; i < array.length; i++) this.array[i] = null;
	}

	/**
	 * Helps in converting to dense representation.
	 */
	public static DenseEdgeList fromSparse(final SparseEdgeList list) {
		final byte[] keys = list.keys();
		final DenseEdgeList newInstance = new DenseEdgeList();
		for (int i = 0; i < keys.length; i++) {
			newInstance.put(keys[i], list.get(keys[i]));
		}
		return newInstance;
	}

	@Override
	public State get(final byte b) {
		return this.array[b & 0xFF];
	}

	@Override
	public void put(final byte b, final State s) {
		this.array[b & 0xFF] = s;
	}

	@Override
	public byte[] keys() {
		int length = 0;
		for (int i = 0; i < array.length; i++) {
			if (array[i] != null) length++;
		}
		final byte[] result = new byte[length];
		int j = 0;
		for (int i = 0; i < array.length; i++) {
			if (array[i] != null) {
				result[j] = (byte) i;
				j++;
			}
		}
		return result;
	}

}
