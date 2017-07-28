package org.ddd4j.infrastructure.channel.jms;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jms.BytesMessage;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Topic;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.HotSource;
import org.ddd4j.infrastructure.channel.util.SourceListener;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.value.versioned.Committed;

public class JmsHotSource implements HotSource, MessageListener {

	public static class Factory implements HotSource.Factory {

		private final ConnectionFactory factory;

		public Factory(Context context) {
			this.factory = context.get(JmsServiceConfigurer.CONNECTION_FACTORY);
		}

		@Override
		public HotSource createHotSource(Callback callback, SourceListener<ReadBuffer, ReadBuffer> listener) {
			JMSContext jmsContext = factory.createContext(JMSContext.DUPS_OK_ACKNOWLEDGE);
			jmsContext.setExceptionListener(callback::onError);
			return new JmsHotSource(jmsContext, callback, listener);
		}
	}

	static Committed<ReadBuffer, ReadBuffer> converted(BytesMessage message) throws JMSException {
		// TODO
		return null;
	}

	private final JMSContext jmsContext;
	private final SourceListener<ReadBuffer, ReadBuffer> listener;
	private final Callback callback;
	private final Map<String, JMSConsumer> subscriptions;

	JmsHotSource(JMSContext jmsContext, Callback callback, SourceListener<ReadBuffer, ReadBuffer> listener) {
		this.jmsContext = Require.nonNull(jmsContext);
		this.callback = Require.nonNull(callback);
		this.listener = Require.nonNull(listener);
		this.subscriptions = new ConcurrentHashMap<>();
	}

	@Override
	public void closeChecked() {
		jmsContext.close();
	}

	@Override
	public void onMessage(Message message) {
		try {
			ResourceDescriptor resource = ResourceDescriptor.of(message.getJMSType());
			Committed<ReadBuffer, ReadBuffer> committed = converted((BytesMessage) message);
			listener.onNext(resource, committed);
		} catch (Exception e) {
			callback.onError(e);
		}
	}

	@Override
	public Promise<Integer> subscribe(ResourceDescriptor resource) {
		subscriptions.computeIfAbsent(resource.value(), this::subscribe);
		return Promise.completed(1);
	}

	private JMSConsumer subscribe(String resource) {
		Topic topic = jmsContext.createTopic(resource);
		JMSConsumer consumer = jmsContext.createSharedConsumer(topic, null);
		consumer.setMessageListener(this);
		return consumer;
	}

	@Override
	public void unsubscribe(ResourceDescriptor resource) {
		JMSConsumer consumer = subscriptions.remove(resource.value());
		if (consumer != null) {
			consumer.close();
		}
	}
}
