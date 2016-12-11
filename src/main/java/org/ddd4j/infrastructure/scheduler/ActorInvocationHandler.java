package org.ddd4j.infrastructure.scheduler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

import org.ddd4j.value.Throwing.TFunction;
import org.ddd4j.value.Type;

public class ActorInvocationHandler implements InvocationHandler {

	private static final Collection<Class<?>> SCHEDULABLE_RETURN_TYPES = Arrays.asList(void.class, Future.class, CompletionStage.class,
			CompletableFuture.class);

	public static <T> T create(Scheduler scheduler, Type<T> type, T delegate) {
		ActorInvocationHandler handler = new ActorInvocationHandler(scheduler, delegate);
		Object proxy = Proxy.newProxyInstance(type.getClassLoader(), type.getInterfaceClosure(), handler);
		return type.cast(proxy);
	}

	private final Actor.Transitioning<Object> actor;

	private ActorInvocationHandler(Scheduler scheduler, Object delegate) {
		this.actor = Actor.create(scheduler, delegate);
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		TFunction<Object, Object> fn = o -> method.invoke(o, args);
		if (SCHEDULABLE_RETURN_TYPES.contains(method.getReturnType())) {
			Task<Object, Object> task = new Task<>(actor.getExecutor(), fn);
			actor.perform(task::executeWith);
			return task;
		} else {
			return actor.ask(fn);
		}
	}
}