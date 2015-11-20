package org.ddd4j;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builder for java {@link Type}.
 *
 * @author sven.linstaedt
 *
 * @param <T> The builder stack's type
 */
public abstract class TypeBuilder<T extends Type> implements Type, Serializable {

    public enum BoundType {
        /**
         * Type binding as:
         *
         * <pre>
         * ? super X
         * </pre>
         */
        LOWER(),
        /**
         * Type binding as:
         *
         * <pre>
         * ? extends X & Y & Z
         * </pre>
         */
        UPPER(Object.class);

        private final Type[] defaultBounds;

        BoundType(Type... defaultBounds) {
            this.defaultBounds = requireNonNull(defaultBounds);
        }

        public Type[] boundsOf(BoundType boundType, List<Type> bounds) {
            if (boundType == this) {
                if (bounds != null && !bounds.isEmpty()) {
                    return bounds.toArray(new Type[bounds.size()]);
                } else {
                    return this.getDefaultBounds();
                }
            } else {
                return getDefaultBounds();
            }
        }

        public Type[] getDefaultBounds() {
            return defaultBounds;
        }
    }

    public static class GenericArray<T extends Type> extends TypeBuilder<T> implements GenericArrayType {

        private static class Builder extends GenericArray<GenericArrayType> {

            private static final long serialVersionUID = 1L;

            protected Builder() {
                super(null);
            }

            @Override
            protected GenericArrayType parent() {
                return this;
            }
        }

        private static final long serialVersionUID = 1L;

        private final T parent;
        private Type componentType;

        protected GenericArray(T parent) {
            this.parent = parent != null ? parent : parent();
        }

        @Override
        public Type getGenericComponentType() {
            return requireNonNull(componentType);
        }

        @Override
        public boolean isValid() {
            return componentType != null;
        }

        @Override
        protected T parent() {
            return parent;
        }

        public GenericArray<T> withComponentType(Type componentType) {
            this.componentType = requireNonNull(componentType);
            return this;
        }

        public GenericArray<GenericArray<T>> withGenericArrayComponentType() {
            GenericArray<GenericArray<T>> child = new GenericArray<>(this);
            componentType = child;
            return child;
        }

        public Parameterized<GenericArray<T>> withParameterizedComponentType(Class<?> rawComponentType) {
            Parameterized<GenericArray<T>> child = new Parameterized<>(this, rawComponentType);
            componentType = child;
            return child;
        }

        public <D extends GenericDeclaration> Variable<GenericArray<T>, D> withTypeVariableComponentType(
                D declaration,
                String name) {
            Variable<GenericArray<T>, D> child = new Variable<>(this, declaration, name);
            componentType = child;
            return child;
        }

        public Wildcard<GenericArray<T>> withWildcardComponentType(BoundType boundType) {
            Wildcard<GenericArray<T>> child = new Wildcard<>(this, boundType);
            componentType = child;
            return child;
        }
    }

    public static class Parameterized<T extends Type> extends TypeBuilder<T> implements ParameterizedType {

        private static class Builder extends Parameterized<ParameterizedType> {

            private static final long serialVersionUID = 1L;

            protected Builder(Class<?> rawType) {
                super(null, rawType);
            }

            @Override
            protected ParameterizedType parent() {
                return this;
            }
        }

        private static final long serialVersionUID = 1L;

        private final T parent;
        private final Class<?> rawType;
        private final List<Type> typeArguments;
        private Type ownerType;

        protected Parameterized(T parent, Class<?> rawType) {
            this.parent = parent != null ? parent : parent();
            this.rawType = requireNonNull(rawType);
            this.typeArguments = new ArrayList<>();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return typeArguments.toArray(new Type[typeArguments.size()]);
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public boolean isValid() {
            return rawType.getTypeParameters().length == typeArguments.size();
        }

        public Parameterized<T> ownedBy(Type ownerType) {
            this.ownerType = requireNonNull(ownerType);
            return this;
        }

        @Override
        protected T parent() {
            return parent;
        }

        public GenericArray<Parameterized<T>> withGenericArrayTypeArgument() {
            GenericArray<Parameterized<T>> child = new GenericArray<>(this);
            typeArguments.add(child);
            return child;
        }

        public Parameterized<Parameterized<T>> withParameterizedTypeArgument(Class<?> rawTypeArgument) {
            Parameterized<Parameterized<T>> child = new Parameterized<>(this, rawTypeArgument);
            typeArguments.add(child);
            return child;
        }

        public Parameterized<T> withTypeArgument(Type typeArgument) {
            typeArguments.add(requireNonNull(typeArgument));
            return this;
        }

        public <D extends GenericDeclaration> Variable<Parameterized<T>, D> withTypeVariableArgument(D declaration, String name) {
            Variable<Parameterized<T>, D> child = new Variable<>(this, declaration, name);
            typeArguments.add(child);
            return child;
        }

        public Wildcard<Parameterized<T>> withWildcardTypeArgument(BoundType boundType) {
            Wildcard<Parameterized<T>> child = new Wildcard<>(this, boundType);
            typeArguments.add(child);
            return child;
        }
    }

    public static class Variable<T extends Type, X extends GenericDeclaration> extends TypeBuilder<T> implements Type,
            AnnotatedElement {

        private static class Builder<X extends GenericDeclaration> extends Variable<Variable<?, X>, X> {

            private static final long serialVersionUID = 1L;

            protected Builder(X declaration, String name) {
                super(null, declaration, name);
            }

            @Override
            protected Variable<?, X> parent() {
                return this;
            }
        }

        private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];

        private static final long serialVersionUID = 1L;

        private final T parent;
        private final X declaration;
        private final String name;
        private final List<Type> bounds;

        protected Variable(T parent, X declaration, String name) {
            this.parent = parent != null ? parent : parent();
            this.declaration = requireNonNull(declaration);
            this.name = requireNonNull(name);
            this.bounds = new ArrayList<>();
        }

        public Type[] getBounds() {
            return BoundType.UPPER.boundsOf(BoundType.UPPER, bounds);
        }

        public X getGenericDeclaration() {
            return declaration;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
            return false;
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return EMPTY_ANNOTATION_ARRAY;
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return EMPTY_ANNOTATION_ARRAY;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        protected T parent() {
            return parent;
        }

        public Variable<T, X> withBound(Type bound) {
            this.bounds.add(requireNonNull(bound));
            return this;
        }

        public Variable<T, X> withBounds(Type... bounds) {
            this.bounds.addAll(Arrays.asList(bounds));
            return this;
        }

        public GenericArray<Variable<T, X>> withGenericArrayTypeBound() {
            GenericArray<Variable<T, X>> child = new GenericArray<>(this);
            bounds.add(child);
            return child;
        }

        public Parameterized<Variable<T, X>> withParameterizedTypeBound(Class<?> rawTypeArgument) {
            Parameterized<Variable<T, X>> child = new Parameterized<>(this, rawTypeArgument);
            bounds.add(child);
            return child;
        }

        public <D extends GenericDeclaration> Variable<Variable<T, X>, D> withTypeVariableBound(D declaration, String name) {
            Variable<Variable<T, X>, D> child = new Variable<>(this, declaration, name);
            bounds.add(child);
            return child;
        }

        public Wildcard<Variable<T, X>> withWildcardTypeBound(BoundType boundType) {
            Wildcard<Variable<T, X>> child = new Wildcard<>(this, boundType);
            bounds.add(child);
            return child;
        }
    }

    public static class Wildcard<T extends Type> extends TypeBuilder<T> implements WildcardType {

        private static class Builder extends Wildcard<WildcardType> {

            private static final long serialVersionUID = 1L;

            protected Builder(BoundType boundType) {
                super(null, boundType);
            }

            @Override
            protected WildcardType parent() {
                return this;
            }
        }

        private static final long serialVersionUID = 1L;

        private final T parent;
        private final BoundType boundType;
        private final List<Type> bounds;

        protected Wildcard(T parent, BoundType boundType) {
            this.parent = parent != null ? parent : parent();
            this.boundType = requireNonNull(boundType);
            this.bounds = new ArrayList<>();
        }

        @Override
        public Type[] getLowerBounds() {
            return BoundType.LOWER.boundsOf(boundType, bounds);
        }

        @Override
        public Type[] getUpperBounds() {
            return BoundType.UPPER.boundsOf(boundType, bounds);
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        protected T parent() {
            return parent;
        }

        public Wildcard<T> withBound(Type bound) {
            this.bounds.add(requireNonNull(bound));
            return this;
        }

        public Wildcard<T> withBounds(Type... bounds) {
            this.bounds.addAll(Arrays.asList(bounds));
            return this;
        }

        public GenericArray<Wildcard<T>> withGenericArrayTypeBound() {
            GenericArray<Wildcard<T>> child = new GenericArray<>(this);
            bounds.add(child);
            return child;
        }

        public Parameterized<Wildcard<T>> withParameterizedTypeBound(Class<?> rawTypeArgument) {
            Parameterized<Wildcard<T>> child = new Parameterized<>(this, rawTypeArgument);
            bounds.add(child);
            return child;
        }

        public <D extends GenericDeclaration> Variable<Wildcard<T>, D> withTypeVariableBound(D declaration, String name) {
            Variable<Wildcard<T>, D> child = new Variable<>(this, declaration, name);
            bounds.add(child);
            return child;
        }

        public Wildcard<Wildcard<T>> withWildcardTypeBound(BoundType boundType) {
            Wildcard<Wildcard<T>> child = new Wildcard<>(this, boundType);
            bounds.add(child);
            return child;
        }
    }

    public static GenericArray<GenericArrayType> genericArrayType() {
        return new GenericArray.Builder();
    }

    public static Parameterized<ParameterizedType> parameterizedType(Class<?> rawType) {
        return new Parameterized.Builder(rawType);
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> rawtype(Class<? super T> type) {
        return (Class<T>) type;
    }

    public static <D extends GenericDeclaration> Variable<Variable<?, D>, D> typeVariable(D declaration, String name) {
        return new Variable.Builder<>(declaration, name);
    }

    public static Wildcard<WildcardType> wildcardType(BoundType boundType) {
        return new Wildcard.Builder(boundType);
    }

    public static final Type[] EMPTY_TYPE_ARRAY = new Type[0];

    private static final long serialVersionUID = 1L;

    public T build() {
        if (isValid()) {
            return parent();
        } else {
            throw new IllegalStateException(this + " is not valid");
        }
    }

    public Type buildAll() {
        Type type = this;
        while (type instanceof TypeBuilder) {
            type = ((TypeBuilder<?>) type).build();
        }
        return type;
    }

    public abstract boolean isValid();

    protected abstract T parent();
}