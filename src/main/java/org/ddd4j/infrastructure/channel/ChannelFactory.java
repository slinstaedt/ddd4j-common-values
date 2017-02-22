package org.ddd4j.infrastructure.channel;

public interface ChannelFactory {

	ColdChannel createColdChannel();

	HotChannel createHotChannel();

	boolean register(ColdChannel.Listener listener);

	boolean register(HotChannel.Listener listener);
}
