package org.ddd4j.spi;

import org.ddd4j.value.collection.Configuration;

@FunctionalInterface
public interface ServiceFactory<T> {

	T create(Context context, Configuration configuration);
}
