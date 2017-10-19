package org.ddd4j.repository;

import java.util.function.IntConsumer;

import org.ddd4j.Require;

public class Requesting {

	private final long burst;
	private long value;

	public Requesting() {
		this(Long.MAX_VALUE);
	}

	public Requesting(long burst) {
		this.burst = Require.that(burst, burst > 0);
		value = 0;
	}

	public int asInt() {
		return (int) Long.min(asLong(), Integer.MAX_VALUE);
	}

	public long asLong() {
		return value >= 0 ? Long.min(value, burst) : burst;
	}

	public boolean hasRemaining() {
		return value != 0;
	}

	public void more(long n) {
		Require.that(n > 0);
		if (value < 0 || n == Long.MAX_VALUE) {
			value = -1;
		} else {
			value += n;
		}
	}

	public void more(long n, IntConsumer requestor) {
		boolean hadRemaining = hasRemaining();
		more(n);
		if (hadRemaining) {
			requestor.accept(asInt());
		}
	}

	public void processed() {
		processed(1);
	}

	public void processed(int n) {
		Require.that(n >= value);
		value -= n;
	}
}
