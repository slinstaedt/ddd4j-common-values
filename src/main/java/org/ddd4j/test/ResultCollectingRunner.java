package org.ddd4j.test;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

import junit.framework.AssertionFailedError;

public class ResultCollectingRunner extends Runner implements Comparable<ResultCollectingRunner> {

	@RunListener.ThreadSafe
	private static class Listener extends RunListener {

		private volatile State state;

		@Override
		public void testFailure(Failure failure) throws Exception {
			state = State.FAILED;
		}
	}

	private enum State {
		FAILED, PASSED, SKIPPED;
	}

	private final Runner delegate;
	private final List<ResultCollectingRunner> dependencies;
	private final Listener listener;

	ResultCollectingRunner(Runner delegate) {
		this.delegate = requireNonNull(delegate);
		this.dependencies = new ArrayList<>();
		this.listener = new Listener();
	}

	public void addDependencies(List<Runner> dependencies) {
		dependencies.stream()
				.map(r -> r instanceof ResultCollectingRunner ? (ResultCollectingRunner) r : new ResultCollectingRunner(r))
				.collect(Collectors.toCollection(() -> this.dependencies));
	}

	private void addFailureDescription(Consumer<Description> collector) {
		if (listener.state != State.PASSED) {
			collector.accept(Description.createTestDescription(delegate.getDescription().getTestClass(), listener.state.name()));
		}
	}

	@Override
	public int compareTo(ResultCollectingRunner other) {
		return this == other ? 0 : dependsOn(other) ? 1 : -1;
	}

	private boolean dependsOn(ResultCollectingRunner other) {
		return dependencies.contains(other) || dependencies.stream().anyMatch(r -> r.dependsOn(other));
	}

	@Override
	public Description getDescription() {
		return delegate.getDescription();
	}

	@Override
	public void run(RunNotifier notifier) {
		listener.state = State.PASSED;
		List<Description> depFailures = new ArrayList<>();
		dependencies.stream().forEach(r -> r.addFailureDescription(depFailures::add));
		if (depFailures.isEmpty()) {
			notifier.addListener(listener);
			try {
				delegate.run(notifier);
			} finally {
				notifier.removeListener(listener);
			}
		} else {
			listener.state = State.SKIPPED;
			Description description = delegate.getDescription();
			depFailures.forEach(description::addChild);
			notifier.fireTestAssumptionFailed(new Failure(description, new AssertionFailedError(depFailures.toString())));
		}
	}

	@Override
	public String toString() {
		return getDescription().toString();
	}
}
