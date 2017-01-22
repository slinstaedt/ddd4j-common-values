package org.ddd4j.infrastructure.channel;

import org.ddd4j.infrastructure.ResourceDescriptor;

public interface HotSource extends AutoCloseable {

	void subscribe(ResourceDescriptor topic, SourceSubscriber subscriber, RevisionsCallback callback) throws Exception;
}