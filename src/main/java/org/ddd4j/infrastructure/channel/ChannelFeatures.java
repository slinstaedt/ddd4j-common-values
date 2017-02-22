package org.ddd4j.infrastructure.channel;

public interface ChannelFeatures {

	enum ChannelType {
		COLD, HOT;
	}

	ChannelFeature<ChannelType> CHANNEL_TYPE = ChannelFeature.newFeature();
}
