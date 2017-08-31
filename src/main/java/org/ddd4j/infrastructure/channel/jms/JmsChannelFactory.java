package org.ddd4j.infrastructure.channel.jms;

import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;

import org.ddd4j.infrastructure.channel.HotSource;
import org.ddd4j.infrastructure.channel.HotSource.Callback;
import org.ddd4j.infrastructure.channel.Writer;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.util.SourceListener;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;
import org.ddd4j.spi.ServiceBinder;
import org.ddd4j.spi.ServiceConfigurer;

public class JmsChannelFactory implements HotSource.Factory, Writer.Factory {

	public static class JmsServiceConfigurer implements ServiceConfigurer {

		@Override
		public void bindServices(ServiceBinder binder) {
			binder.bind(HotSource.FACTORY).toDelegate(JmsChannelFactory.KEY);
			binder.bind(Writer.FACTORY).toDelegate(JmsChannelFactory.KEY);
		}
	}

	public static final Key<JmsChannelFactory> KEY = Key.of(JmsChannelFactory.class, JmsChannelFactory::new);
	public static final Key<ConnectionFactory> CONNECTION_FACTORY = Key.reflective(ConnectionFactory.class);

	private final ConnectionFactory factory;

	public JmsChannelFactory(Context context) {
		this.factory = context.get(CONNECTION_FACTORY);
	}

	@Override
	public HotSource createHotSource(Callback callback, SourceListener<ReadBuffer, ReadBuffer> listener) {
		JMSContext jmsContext = factory.createContext(JMSContext.DUPS_OK_ACKNOWLEDGE);
		jmsContext.setExceptionListener(listener::onError);
		return new JmsHotSource(jmsContext, callback, listener);
	}

	@Override
	public Writer<ReadBuffer, ReadBuffer> createWriter(ChannelName name) {
		// TODO Auto-generated method stub
		return null;
	}
}
