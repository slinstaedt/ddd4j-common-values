package org.ddd4j.infrastructure.scheduler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.ddd4j.value.Throwing.TFunction;
import org.ddd4j.value.Type;

public class ActorInvocationHandler implements InvocationHandler {

	public static <T> T create(Scheduler scheduler, Type<T> type, T delegate) {
		ActorInvocationHandler handler = new ActorInvocationHandler(scheduler, delegate);
		Object proxy = Proxy.newProxyInstance(type.getClassLoader(), type.getInterfaceClosure(), handler);
		return type.cast(proxy);
	}

	private final Actor<Object> actor;

	private ActorInvocationHandler(Scheduler scheduler, Object delegate) {
		this.actor = Actor.create(scheduler, delegate);
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		TFunction<Object, Object> fn = o -> method.invoke(o, args);
		if (method.getReturnType().isAssignableFrom(Task.class)) {
			Task<Object, Object> task = new Task<>(actor.getExecutor(), fn);
			actor.perform(task::executeWith);
			return task;
		} else {
			return actor.ask(fn);
		}
	}
}