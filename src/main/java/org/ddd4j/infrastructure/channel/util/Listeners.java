package org.ddd4j.infrastructure.channel.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ChannelName;
import org.ddd4j.infrastructure.channel.DataAccessFactory;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;

public class Listeners {

	private final ChannelName resource;
	private final Promise<Integer> partitionSize;
	private final Runnable closer;
	private final Map<Object, SourceListener<ReadBuffer, ReadBuffer>> listeners;

	public Listeners(ChannelName resource, Promise<Integer> partitionSize, Runnable closer) {
		this.resource = Require.nonNull(resource);
		this.partitionSize = Require.nonNull(partitionSize);
		this.closer = Require.nonNull(closer);
		this.listeners = new ConcurrentHashMap<>();
	}

	public Listeners add(SourceListener<ReadBuffer, ReadBuffer> listener) {
		return add(listener, listener);
	}

	public Listeners add(Object handle, SourceListener<ReadBuffer, ReadBuffer> listener) {
		listeners.put(Require.nonNull(handle), Require.nonNull(listener));
		return this;
	}

	public void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
		listeners.values().forEach(l -> l.onNext(resource, DataAccessFactory.resetBuffers(committed)));
	}

	public Promise<Integer> partitionSize() {
		return partitionSize;
	}

	public Listeners remove(Object handle) {
		listeners.remove(handle);
		if (listeners.isEmpty()) {
			closer.run();
			return null;
		} else {
			return this;
		}
	}
}