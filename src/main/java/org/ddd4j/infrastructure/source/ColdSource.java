package org.ddd4j.infrastructure.source;

import org.ddd4j.infrastructure.ResourceDescriptor;

public interface ColdSource {

	void load(ResourceDescriptor descriptor, Subscriber subscriber) throws Exception;
}