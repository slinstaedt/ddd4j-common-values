package org.ddd4j.infrastructure.scheduler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.ddd4j.Throwing.TFunction;
import org.ddd4j.value.Type;

public class AgentInvocationHandler implements InvocationHandler {

	public static <T> T create(Scheduler scheduler, Type<T> type, T delegate) {
		AgentInvocationHandler handler = new AgentInvocationHandler(scheduler, delegate);
		Object proxy = Proxy.newProxyInstance(type.getClassLoader(), type.getInterfaceClosure(), handler);
		return type.cast(proxy);
	}

	private final Agent<Object> actor;

	private AgentInvocationHandler(Scheduler scheduler, Object delegate) {
		this.actor = Agent.create(scheduler, delegate);
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