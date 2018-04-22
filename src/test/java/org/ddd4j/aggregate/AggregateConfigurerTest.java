package org.ddd4j.aggregate;

import java.util.concurrent.atomic.AtomicReference;

import org.ddd4j.aggregate.CommandHandlerConfigurer;
import org.ddd4j.log.Log;
import org.ddd4j.spi.TestProvisioning;
import org.ddd4j.util.Props;
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
		provisioning.withConfigurer(b -> b.bind(ChannelPublisher.KEY).toInstance(null));
		provisioning.withConfigurer(new CommandHandlerConfigurer());
		provisioning.withAggregate(ref::set);

		provisioning.createContext(Props.EMPTY);

		Assert.assertNotNull(ref.get());
	}
}
