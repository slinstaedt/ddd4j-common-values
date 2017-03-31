package org.ddd4j.spi;

import java.util.concurrent.atomic.AtomicLong;

import org.ddd4j.value.collection.Props;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ContextProvisioningTest {

	private TestProvisioning provisioning;

	@Before
	public void init() {
		provisioning = new TestProvisioning();
	}

	@Test
	public void initSimpleContext() {
		Context context = provisioning.createContext(Props.EMTPY);

		Assert.assertNotNull(context.get(ContextProvisioning.KEY));
	}

	@Test
	public void initEagerContext() {
		AtomicLong value = new AtomicLong(0);
		Key<Long> eagerKey = Key.of(Long.class, ctx -> value.incrementAndGet());
		provisioning.withConfigurer(b -> b.initializeEager(eagerKey));

		provisioning.createContext(Props.EMTPY);

		Assert.assertEquals(1, value.get());
	}
}
