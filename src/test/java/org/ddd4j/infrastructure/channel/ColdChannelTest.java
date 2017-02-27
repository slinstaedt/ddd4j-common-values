package org.ddd4j.infrastructure.channel;

import java.nio.ByteBuffer;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdChannel.Callback;
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

public abstract class ColdChannelTest {

	public static final String TEST_TOPIC = "TEST";

	private ColdChannel testUnit;
	private ResourceDescriptor topic;
	private Revisions revisions;

	@Before
	public void before() {
		testUnit = createChannel();
		topic = ResourceDescriptor.of(TEST_TOPIC);
		revisions = new Revisions(1);

		revisions.updateWithPartition(0, 0);
	}

	protected abstract ColdChannel createChannel();

	@Test
	public void receiveFromTopic() {
		send("1", revisions);
		send("2", revisions);
		send("3", revisions);
		TestChannelListener listener = new TestChannelListener();

		Callback callback = testUnit.register(listener);
		callback.seek(topic, new Revision(0, 0));

		listener.failOnErrors();
		Assert.assertFalse(listener.getMessages().isEmpty());
	}

	private CommitResult<ReadBuffer, ReadBuffer> send(String message, Revisions expected) {
		ReadBuffer keyBuffer = Bytes.wrap(ByteBuffer.allocate(1024)).buffered().putUTF("key-" + message).flip();
		ReadBuffer valueBuffer = Bytes.wrap(ByteBuffer.allocate(1024)).buffered().putUTF("value-" + message).flip();
		Uncommitted<ReadBuffer, ReadBuffer> uncommitted = Recorded.uncommitted(keyBuffer, valueBuffer, expected);

		CommitResult<ReadBuffer, ReadBuffer> result = testUnit.trySend(topic, uncommitted).join();

		result.visitCommitted(c -> revisions.update(c.getNextExpected()));
		Assert.assertThat(result, CoreMatchers.instanceOf(Committed.class));
		Assert.assertThat(result.foldResult(c -> c.getNextExpected(), c -> Revision.UNKNOWN_OFFSET),
				CoreMatchers.is(revisions.revisionOfPartition(0)));
		return result;
	}

	@Test
	public void sendToTopic() {
		send("1", revisions);
		send("2", revisions);
		send("3", revisions);
	}
}
