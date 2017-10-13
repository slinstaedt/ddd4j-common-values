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
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RepartitioningListener;
import org.ddd4j.infrastructure.channel.spi.DataAccessFactory;
import org.ddd4j.infrastructure.channel.spi.HotSource;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Props;
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
	private final CommitListener<ReadBuffer, ReadBuffer> commit;
	private final Map<ChannelName, Promise<JMSConsumer>> subscriptions;
	private final ErrorListener error;
	private final RepartitioningListener repartitioning;

	JmsHotSource(Agent<JMSContext> client, CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error,
			RepartitioningListener repartitioning) {
		this.client = Require.nonNull(client);
		this.commit = Require.nonNull(commit);
		this.error = Require.nonNull(error);
		this.repartitioning = Require.nonNull(repartitioning);
		this.subscriptions = new ConcurrentHashMap<>();
	}

	@Override
	public void closeChecked() {
		// TODO remove from interface?
	}

	@Override
	public Promise<Integer> subscribe(ChannelName name) {
		return subscriptions.computeIfAbsent(name, this::onSubscribed).thenReturnValue(JmsChannelFactory.PARTITION_COUNT);
	}

	private Promise<JMSConsumer> onSubscribed(ChannelName name) {
		String subscriptionName = null; // TODO
		return client.perform(ctx -> ctx.createSharedConsumer(ctx.createTopic(name.value()), subscriptionName))
				.whenCompleteSuccessfully(c -> c.setMessageListener(msg -> {
					try {
						Committed<ReadBuffer, ReadBuffer> committed = converted((BytesMessage) msg);
						commit.onNext(name, committed);
					} catch (Exception e) {
						error.onError(e);
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
