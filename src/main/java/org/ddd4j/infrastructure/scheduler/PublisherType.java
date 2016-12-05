package org.ddd4j.infrastructure.scheduler;

import org.reactivestreams.Publisher;

public enum PublisherType {

	MULTICAST {

		@Override
		public <T> Publisher<T> create(Scheduler scheduler, Publisher<T> delegate) {
			return Multicast.create(scheduler, delegate);
		}
	},
	UNICAST {

		@Override
		public <T> Publisher<T> create(Scheduler scheduler, Publisher<T> delegate) {
			return Unicast.create(scheduler, delegate);
		}
	};

	public abstract <T> Publisher<T> create(Scheduler scheduler, Publisher<T> delegate);
}
