package org.ddd4j.infrastructure.channel.jms;

import java.time.OffsetDateTime;

import javax.jms.BytesMessage;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Topic;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.spi.Writer;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revision;

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

	private Committed<ReadBuffer, ReadBuffer> send(JMSContext ctx, Recorded<ReadBuffer, ReadBuffer> recorded) throws JMSException {
		Topic topic = ctx.createTopic(name.value());
		BytesMessage message = ctx.createBytesMessage();
		convert(recorded, message);
		ctx.createProducer().send(topic, message);
		return recorded.committed(Revision.UNKNOWN, OffsetDateTime.now());
	}

	@Override
	public Promise<Committed<ReadBuffer, ReadBuffer>> put(Recorded<ReadBuffer, ReadBuffer> recorded) {
		return client.perform(ctx -> send(ctx, recorded));
	}
}
