package org.ddd4j.aggregate;

import org.ddd4j.value.Value;
import org.ddd4j.value.math.Ordered;

public interface Version extends Ordered<Version>, Value<Version> {

	Version INITIAL = new Version() {

		@Override
		public int compareTo(Version o) {
			return this == o ? 0 : 1;
		}

		@Override
		public int hash() {
			return 0;
		}
	};

	default boolean after(Version other) {
		return largerThan(other);
	}

	default boolean before(Version other) {
		return smallerThan(other);
	}
}
