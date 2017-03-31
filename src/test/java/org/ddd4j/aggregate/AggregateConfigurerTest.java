package org.ddd4j.aggregate;

import org.ddd4j.log.Log;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.ContextProvisioning;
import org.ddd4j.value.collection.Props;

public class AggregateConfigurerTest {

	public static class TestAggregateConfigurer implements AggregateConfigurer {

		@Override
		public void configure(Log log) {
			// TODO Auto-generated method stub

		}
	}

	public static void main(String[] args) {
		Context context = ContextProvisioning.byJavaServiceLoader().createContext(Props.EMTPY);
	}
}
