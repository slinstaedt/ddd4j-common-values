package org.ddd4j.infrastructure.publisher;

import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RebalanceListener;
import org.ddd4j.infrastructure.channel.spi.FlowControlled;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Throwing.Closeable;

//TODO needed?
public interface ChannelListener
		extends CommitListener<ReadBuffer, ReadBuffer>, ErrorListener, RebalanceListener, FlowControlled<Void>, Closeable {
}