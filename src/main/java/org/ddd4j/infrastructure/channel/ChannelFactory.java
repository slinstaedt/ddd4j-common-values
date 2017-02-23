package org.ddd4j.infrastructure.channel;

import java.util.Optional;

public interface ChannelFactory {

	boolean supports(ChannelFeatures features);

	Optional<ColdChannel> createColdChannel(ChannelFeatures features);

	Optional<HotChannel> createHotChannel(ChannelFeatures features);
}
