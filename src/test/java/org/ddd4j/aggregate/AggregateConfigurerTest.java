package org.ddd4j.aggregate;

import java.util.concurrent.atomic.AtomicReference;

import org.ddd4j.aggregate.CommandHandlerConfigurer;
import org.ddd4j.infrastructure.publisher.ChannelPublisher;
import org.ddd4j.spi.ContextProvisioning;
import org.ddd4j.value.config.Props;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AggregateConfigurerTest {

	private ContextProvisioning.Programmatic provisioning;

	@Before
	public void init() {
		provisioning = ContextProvisioning.programmatic();
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
