package org.ddd4j.infrastructure.channel.jms;

import javax.jms.BytesMessage;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Topic;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.Writer;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.domain.ChannelPartition;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Recorded;

public class JmsWriter implements Writer<ReadBuffer, ReadBuffer> {

	static void convert(Recorded<ReadBuffer, ReadBuffer> recorded, BytesMessage message) throws JMSException {
		message.setJMSCorrelationIDAsBytes(recorded.getKey().toByteArray());
		recorded.getValue().forEachRemaining(message::writeByte);
		// TODO
		throw new UnsupportedOperationException();
	}

	private final Agent<JMSContext> client;
	private final ChannelName name;

	public JmsWriter(Agent<JMSContext> client, ChannelName name) {
		this.client = Require.nonNull(client);
		this.name = Require.nonNull(name);
	}

	private ChannelPartition send(JMSContext ctx, Recorded<ReadBuffer, ReadBuffer> recorded) throws JMSException {
		Topic topic = ctx.createTopic(name.value());
		BytesMessage message = ctx.createBytesMessage();
		convert(recorded, message);
		ctx.createProducer().send(topic, message);
		return new ChannelPartition(name, JmsChannelFactory.PARTITION);
	}

	@Override
	public Promise<ChannelPartition> put(Recorded<ReadBuffer, ReadBuffer> recorded) {
		return client.perform(ctx -> send(ctx, recorded));
	}
}