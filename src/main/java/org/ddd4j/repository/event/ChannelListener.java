package org.ddd4j.repository.event;

import org.ddd4j.Throwing.Closeable;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RebalanceListener;
import org.ddd4j.infrastructure.channel.spi.FlowControlled;
import org.ddd4j.io.ReadBuffer;

public interface ChannelListener
		extends CommitListener<ReadBuffer, ReadBuffer>, ErrorListener, RebalanceListener, FlowControlled<Void>, Closeable {
}