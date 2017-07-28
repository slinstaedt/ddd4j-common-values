package org.ddd4j.value;

public final class Nothing extends Error {

	public static final Nothing INSTANCE = new Nothing();

	private static final long serialVersionUID = 1L;

	@Override
	public boolean equals(Object o) {
		return o instanceof Nothing;
	}

	@Override
	public int hashCode() {
		return 41;
	}
}
