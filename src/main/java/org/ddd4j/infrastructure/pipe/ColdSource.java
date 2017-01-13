package org.ddd4j.infrastructure.pipe;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.ResourceDescriptor;

public interface ColdSource {

	interface Reader {

		void read(Subscriber subscriber) throws Exception;
	}

	void read(ResourceDescriptor descriptor, Subscriber subscriber) throws Exception;

	default Reader reader(ResourceDescriptor descriptor) {
		Require.nonNull(descriptor);
		return subscriber -> read(descriptor, subscriber);
	}
}