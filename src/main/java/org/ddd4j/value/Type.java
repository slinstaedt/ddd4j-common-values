package org.ddd4j.value;

import java.io.Serializable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;

import org.ddd4j.contract.Require;
import org.ddd4j.io.ByteDataInput;
import org.ddd4j.io.ByteDataOutput;

public abstract class Type<T> extends Value.Simple<Type<T>> implements Value<Type<T>>, Serializable {

	private static final long serialVersionUID = 1L;

	public static final class KnownType<T> extends Type<T> {

		private static final long serialVersionUID = 1L;

		private final String className;

		KnownType(Class<?> javaClass) {
			super(javaClass);
			this.className = javaClass.getName();
		}
	}

	public static Type<?> forName(String className) {
		try {
			return of(Class.forName(className));
		} catch (ClassNotFoundException e) {
			return Throwing.unchecked(e);
		}
	}

	public static Type<?> from(ByteDataInput input) {
	}

	public static <T> Type<T> of(Class<T> javaType) {
		return new KnownType<>(javaType);
	}

	private static Class<?> getTypeLiteralSubclass(Class<?> clazz) {
		Class<?> superclass = clazz.getSuperclass();
		if (superclass.equals(Type.class)) {
			return clazz;
		} else if (superclass.equals(Object.class)) {
			return null;
		} else {
			return getTypeLiteralSubclass(superclass);
		}
	}

	private static java.lang.reflect.Type getTypeParameter(Class<?> superclass) {
		java.lang.reflect.Type type = superclass.getGenericSuperclass();
		if (type instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) type;
			if (parameterizedType.getActualTypeArguments().length == 1) {
				return parameterizedType.getActualTypeArguments()[0];
			}
		}
		return null;
	}

	private transient java.lang.reflect.Type actualType;

	protected Type() {
	}

	private Type(Class<?> javaClass) {
		this.actualType = Require.nonNull(javaClass);
	}

	public final java.lang.reflect.Type getGenericType() {
		if (actualType == null) {
			Class<?> typeLiteralSubclass = getTypeLiteralSubclass(getClass());
			if (typeLiteralSubclass == null) {
				throw new RuntimeException(getClass() + " is not a subclass of TypeLiteral");
			}
			actualType = getTypeParameter(typeLiteralSubclass);
			if (actualType == null) {
				throw new RuntimeException(getClass() + " does not specify the type parameter T of TypeLiteral<T>");
			}
		}
		return actualType;
	}

	@SuppressWarnings("unchecked")
	public final Class<T> getRawType() {
		java.lang.reflect.Type type = getGenericType();
		if (type instanceof Class) {
			return (Class<T>) type;
		} else if (type instanceof ParameterizedType) {
			return (Class<T>) ((ParameterizedType) type).getRawType();
		} else if (type instanceof GenericArrayType) {
			return (Class<T>) Object[].class;
		} else {
			throw new RuntimeException("Illegal type");
		}
	}

	@Override
	public void serialize(ByteDataOutput output) {
		output.writeUTF(getRawType().getName());
	}

	@Override
	protected final Object value() {
		return getGenericType();
	}
}
