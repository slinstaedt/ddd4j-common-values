package org.ddd4j.infrastructure.channel.jms;

import javax.jms.ConnectionFactory;

import org.ddd4j.infrastructure.channel.HotSource;
import org.ddd4j.spi.Key;
import org.ddd4j.spi.ServiceBinder;
import org.ddd4j.spi.ServiceConfigurer;

public class JmsServiceConfigurer implements ServiceConfigurer {

	public static final Key<ConnectionFactory> CONNECTION_FACTORY = Key.reflective(ConnectionFactory.class);

	@Override
	public void bindServices(ServiceBinder binder) {
		binder.bind(HotSource.FACTORY, JmsHotSource.Factory::new);
	}
}
