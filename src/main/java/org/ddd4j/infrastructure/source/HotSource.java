package org.ddd4j.infrastructure.source;

import org.ddd4j.infrastructure.ResourceDescriptor;

public interface HotSource {

	interface Subscription {

		void cancel() throws Exception;
	}

	HotSource.Subscription subscribe(ResourceDescriptor descriptor, Subscriber subscriber) throws Exception;
}