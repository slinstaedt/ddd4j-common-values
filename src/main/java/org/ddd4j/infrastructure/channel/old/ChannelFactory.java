package org.ddd4j.infrastructure.channel.old;

import java.util.Optional;

import org.ddd4j.spi.Key;

public interface ChannelFactory {

	Key<ChannelFactory> KEY = Key.of(ChannelFactory.class);

	boolean supports(ChannelFeatures features);

	Optional<ColdChannel> createColdChannel(ChannelFeatures features);

	Optional<HotChannel> createHotChannel(ChannelFeatures features);
}
