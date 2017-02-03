package org.ddd4j.infrastructure.channel;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.Throwing.Closeable;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public interface ChannelListener extends PartitionRebalanceListener {

	interface ColdChannelCallback extends Closeable {

		void seek(ResourceDescriptor topic, Revision revision);

		void unseek(ResourceDescriptor topic);
	}

	interface HotChannelCallback extends Closeable {

		void subscribe(ResourceDescriptor topic);

		void unsubscribe(ResourceDescriptor topic);
	}

	void onError(Throwable throwable);

	void onNext(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed);

	void onColdRegistration(ColdChannelCallback callback);

	void onHotRegistration(HotChannelCallback callback);
}