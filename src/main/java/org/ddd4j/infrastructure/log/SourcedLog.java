package org.ddd4j.infrastructure.log;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdSource;
import org.ddd4j.infrastructure.channel.HotSource;
import org.ddd4j.infrastructure.channel.Subscriber;
import org.ddd4j.infrastructure.channel.HotSource.Subscription;
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
