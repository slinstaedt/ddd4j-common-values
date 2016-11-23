package org.ddd4j.stream;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.ddd4j.contract.Require;

public class SingleThreaded<R extends Runnable> implements Runnable {

	private final R delegate;
	private volatile boolean running;

	public SingleThreaded(R delegate) {
		this.delegate = Require.nonNull(delegate);
		this.running = false;
	}

	public void schedule(Executor executor, Consumer<? super R> task) {

	}

	public R getDelegate() {
		return delegate;
	}

	public boolean isRunning() {
		return running;
	}

	@Override
	public void run() {
		if (!running) {
			running = true;
			try {
				delegate.run();
			} finally {
				running = false;
			}
		}
	}
}
