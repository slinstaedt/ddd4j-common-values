package org.ddd4j.infrastructure.channel.kafka;

import java.util.Properties;

import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.ZkConnection;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.ddd4j.infrastructure.channel.ChannelFeatures;
import org.ddd4j.value.collection.Seq;

import kafka.admin.AdminUtils;
import kafka.utils.ZKStringSerializer$;
import kafka.utils.ZkUtils;

public class KafkaChannelFactory {

	private static final ByteArrayDeserializer DESERIALIZER = new ByteArrayDeserializer();

	private String zookeeperHosts;
	private int sessionTimeoutInMs;
	private int connectionTimeoutInMs;
	private boolean secureConnection;
	private final int partitionsSize = 2;
	private final int replicationCount = 3;

	static Properties propsFor(Seq<String> servers, int timeout) {
		Properties props = new Properties();
		props.setProperty("bootstrap.servers", String.join(",", servers));
		props.setProperty("group.id", null);
		props.setProperty("enable.auto.commit", "false");
		props.setProperty("heartbeat.interval.ms", String.valueOf(timeout / 4));
		props.setProperty("session.timeout.ms", String.valueOf(timeout));
		return props;
	}

	private void createTopic(String topic, ChannelFeatures features) {
		ZkClient zkClient = null;
		try {
			Properties props = new Properties();
			zkClient = new ZkClient(zookeeperHosts, sessionTimeoutInMs, connectionTimeoutInMs, ZKStringSerializer$.MODULE$);
			ZkUtils zkUtils = new ZkUtils(zkClient, new ZkConnection(zookeeperHosts), secureConnection);
			if (!AdminUtils.topicExists(zkUtils, topic)) {
				AdminUtils.createTopic(zkUtils, topic, partitionsSize, replicationCount, props, AdminUtils.createTopic$default$6());
			}
		} catch (TopicExistsException e) {
			// ignore
		} finally {
			if (zkClient != null) {
				zkClient.close();
			}
		}
	}
}
