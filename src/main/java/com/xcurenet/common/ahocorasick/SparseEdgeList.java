package com.xcurenet.common.ahocorasick;

/**
 * Linked list implementation of the EdgeList should be less memory-intensive.
 */
class SparseEdgeList implements EdgeList {
	private Cons head;

	public SparseEdgeList() {
		head = null;
	}

	@Override
	public State get(final byte b) {
		Cons c = head;
		while (c != null) {
			if (c.b == b) return c.s;
			c = c.next;
		}
		return null;
	}

	@Override
	public void put(final byte b, final State s) {
		this.head = new Cons(b, s, head);
	}

	@Override
	public byte[] keys() {
		int length = 0;
		Cons c = head;
		while (c != null) {
			length++;
			c = c.next;
		}
		final byte[] result = new byte[length];
		c = head;
		int j = 0;
		while (c != null) {
			result[j] = c.b;
			j++;
			c = c.next;
		}
		return result;
	}

	static private class Cons {
		byte b;
		State s;
		Cons next;

		public Cons(final byte b, final State s, final Cons next) {
			this.b = b;
			this.s = s;
			this.next = next;
		}
	}

}
