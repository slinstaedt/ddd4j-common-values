package org.ddd4j.infrastructure.channel.kafka;

import java.util.Properties;

import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.ddd4j.value.collection.Seq;

public class KafkaChannelFactory {

	private static final ByteArrayDeserializer DESERIALIZER = new ByteArrayDeserializer();

	static Properties propsFor(Seq<String> servers, int timeout) {
		Properties props = new Properties();
		props.setProperty("bootstrap.servers", String.join(",", servers));
		props.setProperty("group.id", null);
		props.setProperty("enable.auto.commit", "false");
		props.setProperty("heartbeat.interval.ms", String.valueOf(timeout / 4));
		props.setProperty("session.timeout.ms", String.valueOf(timeout));
		return props;
	}
}
