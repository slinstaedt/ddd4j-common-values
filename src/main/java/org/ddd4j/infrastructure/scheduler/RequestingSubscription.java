package org.ddd4j.infrastructure.scheduler;

import org.ddd4j.contract.Require;
import org.reactivestreams.Subscription;

public interface RequestingSubscription extends Subscription {

	default long requesting(long current, long n) {
		Require.that(n > 0);
		Require.that(current >= 0);
		long request = current + n;
		return request > 0 ? request : Long.MAX_VALUE;
	}
}
