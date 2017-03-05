package org.ddd4j.spi;

import org.ddd4j.value.Self;
import org.ddd4j.value.collection.Configuration;
import org.ddd4j.value.collection.Props;

public interface Service<S extends Service<S>> extends Named, Self<S> {

	default Configuration getConfiguration() {
		return Props.EMTPY;
	}
}
