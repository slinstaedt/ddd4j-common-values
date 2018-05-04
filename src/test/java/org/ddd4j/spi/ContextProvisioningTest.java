package org.ddd4j.spi;

import java.util.concurrent.atomic.AtomicLong;

import org.ddd4j.value.config.Props;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ContextProvisioningTest {

	private ContextProvisioning.Programmatic provisioning;

	@Before
	public void init() {
		provisioning = ContextProvisioning.programmatic();
	}

	@Test
	public void initEagerContext() {
		AtomicLong value = new AtomicLong(0);
		Ref<Long> eagerRef = Ref.of(Long.class, ctx -> value.incrementAndGet());
		provisioning.withConfigurer(b -> b.initializeEager(eagerRef));

		provisioning.createContext(Props.EMPTY);

		Assert.assertEquals(1, value.get());
	}

	@Test
	public void initSimpleContext() {
		Context context = provisioning.createContext(Props.EMPTY);

		Assert.assertNotNull(context.get(ContextProvisioning.REF));
	}
}
