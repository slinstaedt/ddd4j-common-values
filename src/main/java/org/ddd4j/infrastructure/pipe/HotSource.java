package org.ddd4j.infrastructure.pipe;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.ResourceDescriptor;

public interface HotSource {

	interface Publisher {

		void subscribe(Subscriber subscriber) throws Exception;
	}

	default Publisher publisher(ResourceDescriptor descriptor) {
		Require.nonNull(descriptor);
		return subscriber -> subscribe(descriptor, subscriber);
	}

	void subscribe(ResourceDescriptor descriptor, Subscriber subscriber) throws Exception;
}