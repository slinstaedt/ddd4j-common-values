package org.ddd4j.aggregate;

import java.util.concurrent.atomic.AtomicReference;

import org.ddd4j.aggregate.AggregateConfigurer.AggregateServiceConfigurer;
import org.ddd4j.collection.Props;
import org.ddd4j.log.Log;
import org.ddd4j.spi.TestProvisioning;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AggregateConfigurerTest {

	private TestProvisioning provisioning;

	@Before
	public void init() {
		provisioning = new TestProvisioning();
	}

	@Test
	public void initialization() {
		AtomicReference<Log> ref = new AtomicReference<>();
		provisioning.withConfigurer(b -> b.bind(Log.KEY).toInstance(new Log() {

			@Override
			public <K, V> Committer<K, V> committer(LogChannel<K, V> channel) {
				return null;
			}

			@Override
			public <K, V> Publisher<K, V> publisher(LogChannel<K, V> channel) {
				return null;
			}
		}));
		provisioning.withConfigurer(new AggregateServiceConfigurer());
		provisioning.withAggregate(ref::set);

		provisioning.createContext(Props.EMTPY);

		Assert.assertNotNull(ref.get());
	}
}
