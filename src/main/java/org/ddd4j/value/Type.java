package org.ddd4j.value;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.TypeVariable;
import java.util.Optional;

import org.apache.commons.lang3.reflect.TypeUtils;
import org.ddd4j.contract.Require;
import org.ddd4j.io.Input;
import org.ddd4j.io.Output;

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

	public static final class Variable<D, T> {

		private final Type<D> declaration;
		private final Type<T> baseType;
		private final TypeVariable<Class<D>> javaVariable;

		public Variable(Type<D> declaration, Type<T> baseType, int index) {
			this.declaration = Require.nonNull(declaration);
			this.baseType = Require.nonNull(baseType);
			this.javaVariable = declaration.getRawType().getTypeParameters()[index];
		}

		public Type<T> getBaseType() {
			return baseType;
		}

		public Type<D> getDeclaration() {
			return declaration;
		}

		public TypeVariable<Class<D>> getJavaVariable() {
			return javaVariable;
		}
	}

	private static final long serialVersionUID = 1L;

	@SuppressWarnings("rawtypes")
	private static final TypeVariable<Class<Type>> T = Type.class.getTypeParameters()[0];

	public static Type<?> forName(String className) {
		try {
			return of(Class.forName(className));
		} catch (ClassNotFoundException e) {
			return Throwing.unchecked(e);
		}
	}

	public static Type<?> from(Input input) {
		// TODO
		throw new UnsupportedOperationException();
	}

	public static <T> Type<T> of(Class<? super T> javaType) {
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

	@SuppressWarnings("unchecked")
	public <X> Type<X> asSubType(Class<? super X> type) {
		if (TypeUtils.isAssignable(getGenericType(), type)) {
			return (Type<X>) this;
		} else {
			throw new ClassCastException("Can not cast: '" + TypeUtils.toString(getGenericType()) + "' to '" + type.getTypeName() + "'");
		}
	}

	public Optional<T> cast(Object value) {
		return isAssignableFrom(value.getClass()) ? Optional.of(getRawType().cast(value)) : Optional.empty();
	}

	public final java.lang.reflect.Type getGenericType() {
		if (actualType == null) {
			actualType = Require.nonNull(TypeUtils.getTypeArguments(getClass(), Type.class).get(T));
		}
		return actualType;
	}

	@SuppressWarnings("unchecked")
	public final Class<T> getRawType() {
		return (Class<T>) TypeUtils.getRawType(getGenericType(), null);
	}

	public boolean isAssignableFrom(java.lang.reflect.Type fromType) {
		return TypeUtils.isAssignable(fromType, getGenericType());
	}

	public Type<?> resolve(TypeVariable<Class<?>> variable) {
		return new Unknown(TypeUtils.getTypeArguments(getGenericType(), variable.getGenericDeclaration()).get(variable));
	}

	public <X> Type<? extends X> resolve(Variable<? super T, X> variable) {
		Type<?> type = new Unknown(TypeUtils.getTypeArguments(getGenericType(), variable.getDeclaration().getRawType()).get(variable.getJavaVariable()));
		return type.asSubType(variable.getBaseType().getRawType());
	}

	@Override
	public void serialize(Output output) throws IOException {
		output.asDataOutput().writeUTF(getRawType().getName());
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
