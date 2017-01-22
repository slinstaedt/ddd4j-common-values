package org.ddd4j.infrastructure.channel;

import org.ddd4j.infrastructure.ResourceDescriptor;

public interface ColdSource extends AutoCloseable {

	void read(ResourceDescriptor topic, SourceSubscriber subscriber, RevisionsCallback callback) throws Exception;
}