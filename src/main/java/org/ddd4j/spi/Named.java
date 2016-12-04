package org.ddd4j.spi;

public interface Named {

	default String getName() {
		return getClass().getTypeName();
	}
}
