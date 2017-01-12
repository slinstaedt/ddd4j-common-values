package org.ddd4j.infrastructure.log;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.pipe.ColdSource;
import org.ddd4j.infrastructure.pipe.HotSource;
import org.ddd4j.infrastructure.pipe.HotSource.Subscription;
import org.ddd4j.infrastructure.pipe.Subscriber;
import org.ddd4j.value.Throwing.Closeable;

public class SourcedLog implements Closeable {

	private ColdSource coldSource;
	private HotSource hotSource;

	@Override
	public void closeChecked() throws Exception {
	}

	public Subscription subscribe(ResourceDescriptor descriptor, Subscriber subscriber) throws Exception {
		coldSource.load(descriptor, subscriber);
		Subscription subscription = hotSource.subscribe(descriptor, subscriber);

		return subscription;
	}
}
