package org.ddd4j.infrastructure.channel.jms;

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
		public HotSource createHotSource(Callback callback) {
			JMSContext jmsContext = factory.createContext(JMSContext.DUPS_OK_ACKNOWLEDGE);
			jmsContext.setExceptionListener(callback::onError);
			return new JmsHotSource(jmsContext, callback);
		}
	}

	static Committed<ReadBuffer, ReadBuffer> converted(BytesMessage message) throws JMSException {
		// TODO
		return null;
	}

	private final JMSContext jmsContext;
	private final Subscriptions subscriptions;

	private final Callback callback;

	public JmsHotSource(JMSContext jmsContext, Callback callback) {
		this.jmsContext = Require.nonNull(jmsContext);
		this.callback = Require.nonNull(callback);
		this.subscriptions = new Subscriptions(this::onSubscribe);
	}

	@Override
	public void closeChecked() {
		jmsContext.close();
	}

	@Override
	public void onMessage(Message message) {
		try {
			subscriptions.onNext(message.getJMSType(), converted((BytesMessage) message));
		} catch (Exception e) {
			callback.onError(e);
		}
	}

	private Listeners onSubscribe(String resource) {
		Topic topic = jmsContext.createTopic(resource);
		JMSConsumer consumer = jmsContext.createSharedConsumer(topic, null);
		consumer.setMessageListener(this);
		return new Listeners(resource, Promise.completed(1), consumer::close);
	}

	@Override
	public Promise<Integer> subscribe(Listener<ReadBuffer, ReadBuffer> listener, ResourceDescriptor resource) {
		return subscriptions.subscribe(listener, resource);
	}

	@Override
	public void unsubscribe(Listener<ReadBuffer, ReadBuffer> listener, ResourceDescriptor resource) {
		subscriptions.unsubscribe(listener, resource);
	}
}
