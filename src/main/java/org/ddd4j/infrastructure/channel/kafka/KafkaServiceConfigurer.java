package org.ddd4j.infrastructure.channel.kafka;

import org.ddd4j.infrastructure.channel.HotSource;
import org.ddd4j.spi.ServiceBinder;
import org.ddd4j.spi.ServiceConfigurer;

public class KafkaServiceConfigurer implements ServiceConfigurer {

	@Override
	public void bindServices(ServiceBinder binder) {
		binder.bind(HotSource.FACTORY, KafkaHotSource.Factory::new);
	}
}
