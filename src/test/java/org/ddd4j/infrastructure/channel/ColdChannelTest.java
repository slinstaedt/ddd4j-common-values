package org.ddd4j.infrastructure.channel;

import java.nio.ByteBuffer;
import java.util.stream.IntStream;

import org.ddd4j.infrastructure.channel.old.ColdChannel;
import org.ddd4j.infrastructure.channel.old.ColdChannel.Callback;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;
import org.ddd4j.value.versioned.Uncommitted;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameter;

public abstract class ColdChannelTest {

	public static final String TEST_TOPIC = "TEST";

	@Parameter
	public ColdChannel testUnit;

	private ChannelName topic;
	private Revisions revisions;

	@Before
	public void before() {
		topic = ChannelName.of(TEST_TOPIC);
		revisions = new Revisions(1);

		revisions.updateWithPartition(0, 0);
	}

	private CommitResult<ReadBuffer, ReadBuffer> send(String message, Revisions expected) {
		ReadBuffer keyBuffer = Bytes.wrap(ByteBuffer.allocate(1024)).buffered().putUTF("key-" + message).flip();
		ReadBuffer valueBuffer = Bytes.wrap(ByteBuffer.allocate(1024)).buffered().putUTF("value-" + message).flip();
		Uncommitted<ReadBuffer, ReadBuffer> uncommitted = Recorded.uncommitted(keyBuffer, valueBuffer, expected);

		CommitResult<ReadBuffer, ReadBuffer> result = testUnit.trySend(topic, uncommitted).join();

		result.onCommitted(c -> revisions.update(c.getNextExpected()));
		Assert.assertThat(result, CoreMatchers.instanceOf(Committed.class));
		Assert.assertThat(result.foldResult(c -> c.getNextExpected(), c -> Revision.UNKNOWN_OFFSET),
				CoreMatchers.is(revisions.revisionOfPartition(0)));
		return result;
	}

	@Test
	public void sendAndReceive() throws InterruptedException {
		int msgCount = 42;
		IntStream.rangeClosed(1, msgCount).forEachOrdered(c -> send(String.valueOf(c), revisions));
		TestChannelListener listener = new TestChannelListener();

		Callback callback = testUnit.register(listener);
		callback.seek(topic, new Revision(0, 0));
		Thread.sleep(500);
		callback.close();

		listener.failOnErrors();
		Assert.assertEquals(msgCount, listener.getMessages().size());
	}
}
