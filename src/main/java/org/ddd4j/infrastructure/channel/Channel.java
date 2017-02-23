package org.ddd4j.infrastructure.channel;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.Throwing.Closeable;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Uncommitted;

public class Channel implements Closeable {

	public class Callback implements ColdChannel.Callback, HotChannel.Callback {

		private final ColdChannel.Callback coldCallback;
		private final HotChannel.Callback hotCallback;

		Callback(ColdChannel.Callback coldCallback, HotChannel.Callback hotCallback) {
			this.coldCallback = Require.nonNull(coldCallback);
			this.hotCallback = Require.nonNull(hotCallback);
		}

		@Override
		public void closeChecked() throws Exception {
			coldCallback.closeChecked();
			hotCallback.closeChecked();
		}

		@Override
		public Promise<Integer> subscribe(ResourceDescriptor topic) {
			return hotCallback.subscribe(topic);
		}

		@Override
		public void unsubscribe(ResourceDescriptor topic) {
			hotCallback.unsubscribe(topic);
		}

		@Override
		public void seek(ResourceDescriptor topic, Revision revision) {
			coldCallback.seek(topic, revision);
		}

		@Override
		public void unseek(ResourceDescriptor topic, int partition) {
			coldCallback.unseek(topic, partition);
		}
	}

	private final ColdChannel coldChannel;
	private final HotChannel hotChannel;

	public Channel(ColdChannel coldChannel, HotChannel hotChannel) {
		this.coldChannel = Require.nonNull(coldChannel);
		this.hotChannel = Require.nonNull(hotChannel);
	}

	@Override
	public void closeChecked() throws Exception {
		coldChannel.closeChecked();
		hotChannel.closeChecked();
	}

	public Callback register(ColdChannel.Listener coldListener, HotChannel.Listener hotListener) {
		ColdChannel.Callback coldCallback = coldChannel.register(coldListener);
		HotChannel.Callback hotCallback = hotChannel.register(hotListener);
		return new Callback(coldCallback, hotCallback);
	}

	public void send(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
		hotChannel.send(topic, committed);
	}

	public Promise<CommitResult<ReadBuffer, ReadBuffer>> trySend(ResourceDescriptor topic, Uncommitted<ReadBuffer, ReadBuffer> attempt) {
		return coldChannel.trySend(topic, attempt);
	}
}
