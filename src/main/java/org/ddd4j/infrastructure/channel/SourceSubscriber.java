package org.ddd4j.infrastructure.channel;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;

public interface SourceSubscriber {

	interface SourceSubscription {

		void cancel();
	}

	void onComplete();

	void onError(Throwable throwable);

	void onNext(Committed<ReadBuffer, ReadBuffer> committed);

	void onSubscribe(SourceSubscription subscription);
}
