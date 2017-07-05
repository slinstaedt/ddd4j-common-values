package org.ddd4j.infrastructure.channel.kafka;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.PartitionInfo;
import org.ddd4j.infrastructure.channel.ColdChannelTest;
import org.ddd4j.infrastructure.channel.old.ColdChannel;
import org.ddd4j.infrastructure.channel.old.kafka.KafkaChannel;
import org.ddd4j.infrastructure.channel.old.kafka.KafkaChannelFactory;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Enclosed.class)
public class KafkaChannelTest {

	@RunWith(Parameterized.class)
	public static class Cold extends ColdChannelTest {

		@Parameters
		public static List<? extends ColdChannel> createChannel() {
			return Arrays.asList(createColdChannel(Executors.newFixedThreadPool(4)), createColdChannel(Executors.newFixedThreadPool(4)));
		}
	}

	@SuppressWarnings("resource")
	static KafkaChannel createColdChannel(Executor executor) {
		AtomicLong offset = new AtomicLong(0);
		MockProducer<byte[], byte[]> producer = new MockProducer<>(true, KafkaChannelFactory.SERIALIZER, KafkaChannelFactory.SERIALIZER);
		return new KafkaChannel(executor::execute, () -> producer, () -> {
			MockConsumer<byte[], byte[]> consumer = new MockConsumer<>(OffsetResetStrategy.NONE);
			Runnable recordCopier = new Runnable() {

				@Override
				public void run() {
					producer.history()
							.stream()
							.map(r -> new ConsumerRecord<>(r.topic(), r.partition(), offset.incrementAndGet(), r.key(), r.value()))
							.forEachOrdered(consumer::addRecord);
					producer.clear();
					consumer.schedulePollTask(this);
				}
			};
			consumer.schedulePollTask(recordCopier);
			consumer.updatePartitions(ColdChannelTest.TEST_TOPIC,
					Arrays.asList(new PartitionInfo(ColdChannelTest.TEST_TOPIC, 0, null, null, null)));
			return consumer;
		});
	}
}
