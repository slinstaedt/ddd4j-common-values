package org.ddd4j.spi;

public interface Named {

	default String name() {
		return getClass().getTypeName();
	}
}
