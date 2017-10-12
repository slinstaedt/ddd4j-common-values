package org.ddd4j.infrastructure.channel.api;

import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.domain.value.ChannelRevision;

@FunctionalInterface
public interface CompletionListener {

	CompletionListener VOID = new CompletionListener() {

		@Override
		public void onComplete(Sequence<ChannelRevision> revisions) {
		}
	};

	void onComplete(Sequence<ChannelRevision> revisions);
}
