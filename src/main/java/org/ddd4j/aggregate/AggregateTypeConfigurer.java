package org.ddd4j.aggregate;

import org.ddd4j.spi.Context;
import org.ddd4j.spi.ServiceConfigurer;
import org.ddd4j.value.behavior.AggregateType;

public class AggregateTypeConfigurer implements ServiceConfigurer.Registered<AggregateType<?, ?, ?, ?>> {

	@Override
	public void configure(Context context, AggregateType<?, ?, ?, ?> type) {
	}
}