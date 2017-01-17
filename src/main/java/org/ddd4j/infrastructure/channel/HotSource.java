package org.ddd4j.infrastructure.channel;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.ResourceDescriptor;

public interface HotSource extends AutoCloseable {

	interface Publisher {

		void subscribe(Subscriber subscriber) throws Exception;
	}

	default Publisher publisher(ResourceDescriptor descriptor) {
		Require.nonNull(descriptor);
		return subscriber -> subscribe(descriptor, subscriber);
	}

	void subscribe(ResourceDescriptor descriptor, Subscriber subscriber) throws Exception;
}