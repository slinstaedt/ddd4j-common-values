package org.ddd4j.infrastructure.channel.api;

@FunctionalInterface
public interface ErrorListener {

	ErrorListener VOID = new ErrorListener() {

		@Override
		public void onError(Throwable throwable) {
		}
	};

	void onError(Throwable throwable);
}
