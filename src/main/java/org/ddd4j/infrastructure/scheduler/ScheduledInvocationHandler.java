package org.ddd4j.infrastructure.scheduler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Type;

public class ScheduledInvocationHandler implements InvocationHandler {

	private static class Task extends CompletableFuture<Object> {

		private final Method method;
		private final Object[] arguments;

		Task(Method method, Object[] arguments) {
			this.method = Require.nonNull(method);
			this.arguments = Require.nonNull(arguments);
		}

		void performOn(Object delegate) {
			try {
				Object result = method.invoke(delegate, arguments);
				complete(result);
			} catch (Exception e) {
				completeExceptionally(e);
			}
		}
	}

	private static final Collection<Class<?>> SCHEDULABLE_RETURN_TYPES = Arrays.asList(void.class, Future.class, CompletionStage.class,
			CompletableFuture.class);

	public static <T> T create(Scheduler scheduler, Type<T> type, T delegate) {
		ScheduledInvocationHandler handler = new ScheduledInvocationHandler(scheduler, delegate);
		Object proxy = Proxy.newProxyInstance(type.getClassLoader(), type.getInterfaceClosure(), handler);
		return type.cast(proxy);
	}

	private final Actor<Task> actor;
	private final Object delegate;

	private ScheduledInvocationHandler(Scheduler scheduler, Object delegate) {
		this.actor = scheduler.createActor(t -> t.performOn(delegate));
		this.delegate = Require.nonNull(delegate);
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if (SCHEDULABLE_RETURN_TYPES.contains(method.getReturnType())) {
			Task task = new Task(method, args);
			actor.handle(task);
			return task;
		} else {
			return method.invoke(delegate, args);
		}
	}
}