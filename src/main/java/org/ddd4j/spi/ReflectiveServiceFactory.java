package org.ddd4j.spi;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.ddd4j.Require;
import org.ddd4j.value.Type;
import org.ddd4j.value.config.Configuration;

public class ReflectiveServiceFactory<T> {

	private static final String TYPE_KEY = "type";
	private static final String PARAM_TYPE_KEY = "paramType";
	private static final String PARAM_VALUE_KEY = "paramValue";

	private final Type<T> baseType;
	private final Configuration conf;

	public ReflectiveServiceFactory(Class<T> baseType, Configuration configuration) {
		this(Type.of(baseType), configuration);
	}

	public ReflectiveServiceFactory(Type<T> baseType, Configuration configuration) {
		this.baseType = Require.nonNull(baseType);
		this.conf = Require.nonNull(configuration);
	}

	protected Optional<?> configration(Class<?> type, String key) {
		if (type == String.class) {
			return conf.getString(key);
		} else if (type == boolean.class || type == Boolean.class) {
			return conf.getBoolean(key);
		} else if (type == int.class || type == Integer.class) {
			return conf.getInteger(key);
		} else if (type == long.class || type == Long.class) {
			return conf.getLong(key);
		} else if (type == Class.class) {
			return conf.getClass(Object.class, key);
		} else {
			return Optional.empty();
		}
	}

	protected Object[] configration(Parameter[] parameters) {
		Object[] values = new Object[parameters.length];
		for (int i = 0; i < values.length; i++) {
			values[i] = configration(parameters[i].getType(), PARAM_VALUE_KEY + i).orElse(null);
			if (values[i] == null) {
				values[i] = configration(parameters[i].getType(), parameters[i].getName()).orElse(null);
			}
		}
		return values;
	}

	protected Constructor<? extends T> constructor(Class<? extends T> type) throws Exception {
		Constructor<?>[] constructors = type.getConstructors();
		switch (constructors.length) {
		case 0:
			throw new IllegalArgumentException("Type has no public constructors: " + type);
		case 1:
			return type.getConstructor(constructors[0].getParameterTypes());
		default:
			return type.getConstructor(constructorParameterTypes());
		}
	}

	protected Class<?>[] constructorParameterTypes() {
		List<Class<?>> result = new ArrayList<>();
		int index = 0;
		Optional<Class<? extends Object>> type;
		while ((type = conf.getClass(Object.class, PARAM_TYPE_KEY + index++)).isPresent()) {
			result.add(type.get());
		}
		return result.toArray(new Class<?>[result.size()]);
	}

	public T create() throws Exception {
		Class<? extends T> type = conf.getClass(baseType.getRawType(), TYPE_KEY).orElseThrow(IllegalArgumentException::new);
		Constructor<? extends T> constructor = constructor(type);
		Object[] arguments = configration(constructor.getParameters());
		return constructor.newInstance(arguments);
	}
}
