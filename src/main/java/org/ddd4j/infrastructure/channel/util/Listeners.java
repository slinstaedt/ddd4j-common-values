package org.ddd4j.infrastructure.channel.util;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.DataAccessFactory;
import org.ddd4j.infrastructure.channel.HotSource.Listener;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;

public class Listeners {

	private final ResourceDescriptor resource;
	private final Promise<Integer> partitionSize;
	private final Runnable closer;
	private final Set<Listener<ReadBuffer, ReadBuffer>> listeners;

	public Listeners(ResourceDescriptor resource, Promise<Integer> partitionSize, Runnable closer) {
		this.resource = Require.nonNull(resource);
		this.partitionSize = Require.nonNull(partitionSize);
		this.closer = Require.nonNull(closer);
		this.listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());
	}

	public Listeners add(Listener<ReadBuffer, ReadBuffer> listener) {
		listeners.add(Require.nonNull(listener));
		return this;
	}

	public void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
		listeners.forEach(l -> l.onNext(resource, DataAccessFactory.resetBuffers(committed)));
	}

	public Promise<Integer> partitionSize() {
		return partitionSize;
	}

	public Listeners remove(Listener<?, ?> listener) {
		listeners.remove(listener);
		if (listeners.isEmpty()) {
			closer.run();
			return null;
		} else {
			return this;
		}
	}
}