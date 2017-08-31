package org.ddd4j.infrastructure.channel.jms;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jms.BytesMessage;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;

import org.ddd4j.Require;
import org.ddd4j.collection.Props;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.DataAccessFactory;
import org.ddd4j.infrastructure.channel.HotSource;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.util.SourceListener;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public class JmsHotSource implements HotSource {

	static Committed<ReadBuffer, ReadBuffer> converted(BytesMessage message) throws JMSException {
		ReadBuffer key = Bytes.wrap(message.getJMSCorrelationIDAsBytes()).buffered();
		ReadBuffer value = Bytes.wrap(message.getBody(byte[].class)).buffered();
		Revision actual = new Revision(JmsChannelFactory.PARTITION, message.getLongProperty("actual"));
		Revision next = new Revision(JmsChannelFactory.PARTITION, message.getLongProperty("next"));
		OffsetDateTime timestamp = Instant.ofEpochSecond(message.getJMSTimestamp())
				.atOffset(ZoneOffset.ofTotalSeconds(message.getIntProperty("zoneOfsset")));
		Props header = Props.deserialize(value);
		return DataAccessFactory.committed(key, value, actual, next, timestamp, header);
	}

	private final Agent<JMSContext> client;
	private final SourceListener<ReadBuffer, ReadBuffer> listener;
	private final Callback callback;
	private final Map<ChannelName, Promise<JMSConsumer>> subscriptions;

	JmsHotSource(Agent<JMSContext> client, Callback callback, SourceListener<ReadBuffer, ReadBuffer> listener) {
		this.client = Require.nonNull(client);
		this.callback = Require.nonNull(callback);
		this.listener = Require.nonNull(listener);
		this.subscriptions = new ConcurrentHashMap<>();
	}

	@Override
	public void closeChecked() {
		// TODO remove from interface?
	}

	@Override
	public Promise<Integer> subscribe(ChannelName name) {
		return subscriptions.computeIfAbsent(name, this::onSubscribed)
				.thenRun(() -> callback.onSubscribed(JmsChannelFactory.PARTITION_COUNT))
				.thenReturnValue(JmsChannelFactory.PARTITION_COUNT);
	}

	private Promise<JMSConsumer> onSubscribed(ChannelName name) {
		String subscriptionName = null; // TODO
		return client.perform(ctx -> ctx.createSharedConsumer(ctx.createTopic(name.value()), subscriptionName))
				.whenCompleteSuccessfully(c -> c.setMessageListener(msg -> {
					try {
						Committed<ReadBuffer, ReadBuffer> committed = converted((BytesMessage) msg);
						listener.onNext(name, committed);
					} catch (Exception e) {
						listener.onError(e);
					}
				}));
	}

	@Override
	public void unsubscribe(ChannelName name) {
		Promise<JMSConsumer> consumer = subscriptions.remove(name);
		if (consumer != null) {
			consumer.whenCompleteSuccessfully(JMSConsumer::close);
		}
	}
}
