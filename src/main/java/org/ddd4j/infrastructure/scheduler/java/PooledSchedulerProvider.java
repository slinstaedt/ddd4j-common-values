package org.ddd4j.infrastructure.scheduler.java;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.infrastructure.scheduler.SchedulerProvider;
import org.ddd4j.spi.ServiceLocator;
import org.ddd4j.value.collection.Configuration;

public class PooledSchedulerProvider implements SchedulerProvider {

	public enum PoolType {
		FORK_JOIN_POOL {
			@Override
			public ExecutorService create(int size) {
				return Executors.newWorkStealingPool(size);
			}
		},
		THREAD_POOL {
			@Override
			public ExecutorService create(int size) {
				return Executors.newFixedThreadPool(size);
			}
		};

		public abstract ExecutorService create(int size);
	}

	static final Configuration.Key<PoolType> POOL_TYPE = Configuration.keyOfEnum(PoolType.class, "pool.type", PoolType.THREAD_POOL);
	static final Configuration.Key<Integer> POOL_SIZE = Configuration.keyOfInteger("pool.size", Runtime.getRuntime().availableProcessors());

	@Override
	public Scheduler provideService(Configuration configuration, ServiceLocator locator) {
		Executor executor = configuration.get(POOL_TYPE).create(configuration.get(POOL_SIZE));
		return executor::execute;
	}
}
