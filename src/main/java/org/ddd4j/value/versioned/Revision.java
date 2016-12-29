package org.ddd4j.value.versioned;

import org.ddd4j.value.Value;
import org.ddd4j.value.math.Ordered;

public class Revision extends Value.Simple<Revision, Long> implements Ordered<Revision> {

	public static final Revision INITIAL = new Revision(0);

	public static final Revision LATEST = new Revision(-1);

	private final long value;

	public Revision(long value) {
		this.value = value;
	}

	public boolean after(Revision other) {
		return largerThan(other);
	}

	public long asLong() {
		return value;
	}

	public boolean before(Revision other) {
		return smallerThan(other);
	}

	@Override
	public int compareTo(Revision other) {
		return Long.compareUnsigned(this.value, other.value);
	}

	public boolean isLatest() {
		return value == -1;
	}

	@Override
	protected Long value() {
		return value;
	}
}
