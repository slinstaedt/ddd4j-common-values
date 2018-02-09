package org.ddd4j.infrastructure.channel;

import java.util.ArrayList;
import java.util.List;

import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.repository.publisher.ChannelListener;
import org.ddd4j.value.versioned.Committed;

public class TestChannelListener implements ChannelListener {

	private final List<Throwable> errors;
	private final List<Committed<ReadBuffer, ReadBuffer>> messages;

	public TestChannelListener() {
		this(new ArrayList<>(), new ArrayList<>());
	}

	public TestChannelListener(List<Throwable> errors, List<Committed<ReadBuffer, ReadBuffer>> messages) {
		this.errors = errors;
		this.messages = messages;
	}

	public void failOnErrors() {
		if (!errors.isEmpty()) {
			AssertionError error = new AssertionError("failed to reveice message");
			errors.forEach(error::addSuppressed);
			throw error;
		}
	}

	public List<Throwable> getErrors() {
		return errors;
	}

	public List<Committed<ReadBuffer, ReadBuffer>> getMessages() {
		return messages;
	}

	@Override
	public void onError(Throwable throwable) {
		errors.add(throwable);
	}

	@Override
	public void onNext(ChannelName topic, Committed<ReadBuffer, ReadBuffer> committed) {
		messages.add(committed);
	}
}
