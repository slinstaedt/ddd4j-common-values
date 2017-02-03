package org.ddd4j.infrastructure.channel;

import org.ddd4j.value.Throwing.Closeable;

public interface Channel extends Closeable {

	void register(ChannelListener listener);
}
