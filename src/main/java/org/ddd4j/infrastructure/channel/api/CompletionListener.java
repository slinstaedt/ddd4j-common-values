package org.ddd4j.infrastructure.channel.api;

@FunctionalInterface
public interface CompletionListener {

	// TODO add ChannelRevision as parameter?
	void onComplete();
}
