package org.ddd4j.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.ddd4j.value.collection.Props;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ContextProvisioningTest {

	static class TestProvisioning implements ContextProvisioning {

		private final List<Object> registered;

		TestProvisioning() {
			this.registered = new ArrayList<>();
		}

		@Override
		public <T> Iterable<T> loadRegistered(Class<T> type, ClassLoader loader) {
			return registered.stream().filter(type::isInstance).map(type::cast).collect(Collectors.toList());
		}

		void withProvider(ServiceProvider provider) {
			registered.add(provider);
		}

		void with(Object any) {
			registered.add(any);
		}
	}

	private TestProvisioning provisioning;

	@Before
	public void init() {
		provisioning = new TestProvisioning();
	}

	@Test
	public void initSimpleContext() {
		Context context = provisioning.buildContext(Props.EMTPY);

		Assert.assertNotNull(context.get(ContextProvisioning.KEY));
	}

	@Test
	public void initEagerContext() {
		AtomicLong value = new AtomicLong(0);
		Key<Long> eagerKey = Key.of(Long.class, ctx -> value.incrementAndGet());
		provisioning.withProvider(b -> b.initializeEager(eagerKey));

		provisioning.buildContext(Props.EMTPY);

		Assert.assertEquals(1, value.get());
	}
}
