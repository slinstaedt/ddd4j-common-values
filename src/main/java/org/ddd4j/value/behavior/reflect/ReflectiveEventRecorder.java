package org.ddd4j.value.behavior.reflect;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.ddd4j.util.Throwing;

public class ReflectiveEventRecorder<T> {

	private static class Event<T> {

		private final Method method;
		private final Object[] arguments;

		public Event(Method method, Object[] arguments) {
			this.method = requireNonNull(method);
			this.arguments = requireNonNull(arguments);
		}

		public T apply(T target) {
			try {
				method.invoke(target, arguments);
				return target;
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				return Throwing.unchecked(e);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T createProxy(Class<T> targetType, InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(targetType.getClassLoader(), new Class<?>[] { targetType }, handler);
	}

	private static Object defaultValueFor(Class<?> type) {
		if (type == boolean.class) {
			return Boolean.FALSE;
		} else if (type.isPrimitive()) {
			return 0;
		} else {
			return null;
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <X> ReflectiveEventRecorder<X> of(Class<? extends X> targetType) {
		return new ReflectiveEventRecorder(targetType);
	}

	private final List<Event<T>> events;
	private final T proxy;

	public ReflectiveEventRecorder(Class<T> targetType) {
		this.events = new ArrayList<>();
		this.proxy = createProxy(targetType, new InvocationHandler() {

			@Override
			public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
				events.add(new Event<>(method, arguments));
				return defaultValueFor(method.getReturnType());
			}
		});
	}

	public void clear() {
		events.clear();
	}

	public ReflectiveEventRecorder<T> record(Consumer<? super T> consumer) {
		consumer.accept(proxy);
		return this;
	}

	public T replay(T target) {
		events.forEach(e -> e.apply(target));
		return target;
	}
}
