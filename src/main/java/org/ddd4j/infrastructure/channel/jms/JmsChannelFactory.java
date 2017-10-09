package org.ddd4j.infrastructure.channel.jms;

import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;

import org.ddd4j.infrastructure.channel.api.SourceListener;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.spi.HotSource;
import org.ddd4j.infrastructure.channel.spi.HotSource.Callback;
import org.ddd4j.infrastructure.channel.spi.Writer;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;
import org.ddd4j.spi.ServiceBinder;
import org.ddd4j.spi.ServiceConfigurer;

public class JmsChannelFactory implements HotSource.Factory, Writer.Factory {

	public static class Configurer implements ServiceConfigurer {

		@Override
		public void bindServices(ServiceBinder binder) {
			binder.bind(HotSource.FACTORY).toDelegate(JmsChannelFactory.KEY);
			binder.bind(Writer.FACTORY).toDelegate(JmsChannelFactory.KEY);
		}
	}

	public static final Key<JmsChannelFactory> KEY = Key.of(JmsChannelFactory.class, JmsChannelFactory::new);
	public static final Key<ConnectionFactory> CONNECTION_FACTORY = Key.reflective(ConnectionFactory.class);
	static final int PARTITION = 0;
	static final int PARTITION_COUNT = PARTITION + 1;

	private final Agent<JMSContext> client;

	public JmsChannelFactory(Context context) {
		JMSContext jmsContext = context.get(CONNECTION_FACTORY).createContext(JMSContext.DUPS_OK_ACKNOWLEDGE);
		this.client = context.get(Scheduler.KEY).createAgent(jmsContext);
	}

	@Override
	public void closeChecked() throws Exception {
		client.executeBlocked((t, u) -> JMSContext::close).join();
	}

	@Override
	public HotSource createHotSource(Callback callback, SourceListener<ReadBuffer, ReadBuffer> listener) {
		return new JmsHotSource(client, callback, listener);
	}

	@Override
	public Writer<ReadBuffer, ReadBuffer> createWriter(ChannelName name) {
		return new JmsWriter(client, name);
	}
}
