package org.ddd4j.infrastructure.channel.api;

@FunctionalInterface
public interface CompletionListener {

	CompletionListener VOID = new CompletionListener() {

		@Override
		public void onComplete() {
		}
	};

	void onComplete();
}
