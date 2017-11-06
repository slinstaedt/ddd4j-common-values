package org.ddd4j.repository;

import java.util.concurrent.atomic.AtomicLong;

import org.ddd4j.Require;

public class Requesting {

	private static final long ZERO = 0L;

	private static long minus(long value, long n) {
		Require.that(value >= n);
		return value - n;
	}

	private static long plus(long value, long n) {
		if (value < ZERO || n == Long.MAX_VALUE) {
			return -1;
		} else {
			return value + n;
		}
	}

	private final int burst;
	private final AtomicLong value;

	public Requesting() {
		this(Integer.MAX_VALUE);
	}

	public Requesting(int burst) {
		this.burst = Require.that(burst, burst > ZERO);
		this.value = new AtomicLong(ZERO);
	}

	public int asInt() {
		int val = value.intValue();
		return val >= ZERO ? Integer.min(val, burst) : burst;
	}

	public long asLong() {
		long val = value.longValue();
		return val >= ZERO ? Long.min(val, burst) : burst;
	}

	public boolean hasRemaining() {
		return value.get() != ZERO;
	}

	public boolean more(long n) {
		Require.that(n > ZERO);
		return value.getAndAccumulate(n, Requesting::plus) == ZERO;
	}

	public boolean processed() {
		return processed(1);
	}

	public boolean processed(int n) {
		Require.that(n > ZERO);
		return value.accumulateAndGet(n, Requesting::minus) == ZERO;
	}
}
