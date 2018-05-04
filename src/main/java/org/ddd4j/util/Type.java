package org.ddd4j.util;

import java.io.Serializable;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.reflect.TypeUtils;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Throwing.TFunction;
import org.ddd4j.util.value.Value;
import org.ddd4j.value.function.Curry;

public abstract class Type<T> extends Value.Simple<Type<T>, java.lang.reflect.Type> implements Value<Type<T>>, Serializable {

	private static final class Known<T> extends Type<T> {

		private static final long serialVersionUID = 1L;

		private final String className;

		private Known(Class<? super T> javaClass) {
			super(javaClass);
			this.className = javaClass.getName();
		}
	}

	private static final class Unknown extends Type<Object> {

		private static final long serialVersionUID = 1L;

		private Unknown(java.lang.reflect.Type javaType) {
			super(javaType);
		}
	}

	public static final class Variable<D, T> extends Type<T> {

		private static final long serialVersionUID = 1L;

		private final Type<D> declaration;
		private final TypeVariable<Class<D>> javaVariable;

		private Variable(Type<D> declaration, Type<T> baseType, int index) {
			super(baseType.getGenericType());
			this.declaration = Require.nonNull(declaration);
			this.javaVariable = declaration.getRawType().getTypeParameters()[index];
		}

		private Variable(TypeVariable<Class<D>> javaVariable) {
			super(javaVariable);
			this.declaration = of(javaVariable.getGenericDeclaration());
			this.javaVariable = Require.nonNull(javaVariable);
		}

		public Type<D> getDeclaration() {
			return declaration;
		}

		public TypeVariable<Class<D>> getJavaVariable() {
			return javaVariable;
		}
	}

	private static final Map<String, Class<?>> PRIMITIVES;

	static {
		PRIMITIVES = Collections.unmodifiableMap(
				Stream.of(boolean.class, byte.class, char.class, short.class, int.class, long.class, float.class, double.class)
						.collect(Collectors.toMap(Class::getName, Function.identity())));
	}

	private static final long serialVersionUID = 1L;

	@SuppressWarnings("rawtypes")
	private static final TypeVariable<Class<Type>> T = Type.class.getTypeParameters()[0];

	private static java.lang.reflect.Type classesFirst(java.lang.reflect.Type... types) {
		Require.that(types.length > 0);
		for (java.lang.reflect.Type type : types) {
			if (!TypeUtils.getRawType(type, null).isInterface()) {
				return type;
			}
		}
		return types[0];
	}

	public static Type<?> deserialize(ReadBuffer buffer) {
		// TODO
		throw new UnsupportedOperationException();
	}

	public static Type<?> forName(String className) {
		Class<?> type = PRIMITIVES.get(className);
		if (type == null) {
			try {
				type = Class.forName(className);
			} catch (ClassNotFoundException e) {
				Throwing.unchecked(e);
			}
		}
		return of(type);
	}

	public static <T> Type<T> of(Class<? super T> javaType) {
		return new Known<>(javaType);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Type<?> of(java.lang.reflect.Type javaType) {
		if (javaType instanceof TypeVariable) {
			return new Variable<>((TypeVariable<Class<Class>>) javaType);
		} else if (javaType instanceof Class) {
			return new Known<>((Class<?>) javaType);
		} else {
			return new Unknown(javaType);
		}
	}

	public static <T> Type<T> ofInstance(T object) {
		@SuppressWarnings("unchecked")
		Class<T> javaType = (Class<T>) Require.nonNull(object).getClass();
		return new Known<>(javaType);
	}

	public static <D, T> Variable<D, T> variable(Class<? super D> declaration, int variableIndex, Class<? super T> baseType) {
		return new Variable<>(of(declaration), of(baseType), variableIndex);
	}

	private transient java.lang.reflect.Type actualType;

	protected Type() {
	}

	private Type(java.lang.reflect.Type javaType) {
		this.actualType = Require.nonNull(javaType);
	}

	public <X> Type<X> asSubType(Class<? extends X> toType) {
		return asSubType(Type.of(toType));
	}

	@SuppressWarnings("unchecked")
	public <X> Type<X> asSubType(Type<? extends X> toType) {
		return TypeUtils.isAssignable(this.getGenericType(), toType.getGenericType()) ? (Type<X>) this
				: castException(toType.getTypeName());
	}

	public T cast(Object value) {
		return isAssignableFrom(value.getClass()) ? getRawType().cast(value) : castException(value);
	}

	private <X> X castException(Object o) {
		return Throwing.unchecked(
				new ClassCastException("Can not cast: '" + String.valueOf(o) + "' to '" + TypeUtils.toString(getGenericType()) + "'"));
	}

	// TODO Curry needed?
	public <P> Curry.Query<P, T> constructor(Type<P> parameterType) {
		try {
			TFunction<P, T> constructor = getRawType().getConstructor(parameterType.getRawType())::newInstance;
			return constructor::apply;
		} catch (NoSuchMethodException | SecurityException e) {
			return Throwing.unchecked(e);
		}
	}

	public ClassLoader getClassLoader() {
		return getRawType().getClassLoader();
	}

	public Stream<Type<?>> getClosure() {
		java.lang.reflect.Type[] result;
		java.lang.reflect.Type type = getGenericType();
		if (type instanceof TypeVariable) {
			result = ((TypeVariable<?>) type).getBounds();
		} else if (type instanceof WildcardType) {
			result = ((WildcardType) type).getUpperBounds();
		} else {
			result = new java.lang.reflect.Type[] { type };
		}
		return Stream.of(result).map(Type::of);
	}

	public final java.lang.reflect.Type getGenericType() {
		if (actualType == null) {
			actualType = Require.nonNull(TypeUtils.getTypeArguments(getClass(), Type.class).get(T));
		}
		return actualType;
	}

	@SuppressWarnings("unchecked")
	public final Class<T> getRawType() {
		java.lang.reflect.Type type = getGenericType();
		while (type instanceof TypeVariable || type instanceof WildcardType) {
			if (type instanceof TypeVariable) {
				type = classesFirst(TypeUtils.getImplicitBounds((TypeVariable<?>) type));
			} else if (type instanceof WildcardType) {
				type = classesFirst(TypeUtils.getImplicitUpperBounds((WildcardType) type)[0]);
			}
		}
		return (Class<T>) TypeUtils.getRawType(type, null);
	}

	public String getTypeName() {
		return toString();
	}

	public boolean isAssignableFrom(java.lang.reflect.Type fromType) {
		return TypeUtils.isAssignable(fromType, getGenericType());
	}

	public Type<?> resolve(TypeVariable<? extends Class<?>> variable) {
		return of(TypeUtils.getTypeArguments(getGenericType(), variable.getGenericDeclaration()).get(variable));
	}

	public <X> Type<X> resolve(Variable<? super T, X> variable) {
		return resolve(variable.getJavaVariable()).asSubType(variable);
	}

	@Override
	public void serialize(WriteBuffer buffer) {
		buffer.putUTF(getGenericType().getTypeName());
	}

	@Override
	protected boolean testEquality(java.lang.reflect.Type t1, java.lang.reflect.Type t2) {
		return TypeUtils.equals(t1, t2);
	}

	@Override
	protected final java.lang.reflect.Type value() {
		return getGenericType();
	}
}
