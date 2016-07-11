package org.ddd4j.generator;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

public class Modifiers {

	private static final Map<Modifier, Integer> VALUES;

	static {
		Map<Modifier, Integer> values = new EnumMap<>(Modifier.class);
		values.put(Modifier.ABSTRACT, java.lang.reflect.Modifier.ABSTRACT);
		values.put(Modifier.DEFAULT, 0);
		values.put(Modifier.FINAL, java.lang.reflect.Modifier.FINAL);
		values.put(Modifier.NATIVE, java.lang.reflect.Modifier.NATIVE);
		values.put(Modifier.PRIVATE, java.lang.reflect.Modifier.PRIVATE);
		values.put(Modifier.PROTECTED, java.lang.reflect.Modifier.PROTECTED);
		values.put(Modifier.PUBLIC, java.lang.reflect.Modifier.PUBLIC);
		values.put(Modifier.STATIC, java.lang.reflect.Modifier.STATIC);
		values.put(Modifier.STRICTFP, java.lang.reflect.Modifier.STRICT);
		values.put(Modifier.SYNCHRONIZED, java.lang.reflect.Modifier.SYNCHRONIZED);
		values.put(Modifier.TRANSIENT, java.lang.reflect.Modifier.TRANSIENT);
		values.put(Modifier.VOLATILE, java.lang.reflect.Modifier.VOLATILE);
		VALUES = Collections.unmodifiableMap(values);
	}

	private static final Map<ElementKind, Integer> ALLOWED;

	static {
		Map<ElementKind, Integer> allowed = new EnumMap<>(ElementKind.class);
		allowed.put(ElementKind.CLASS, java.lang.reflect.Modifier.classModifiers());
		allowed.put(ElementKind.CONSTRUCTOR, java.lang.reflect.Modifier.constructorModifiers());
		allowed.put(ElementKind.FIELD, java.lang.reflect.Modifier.fieldModifiers());
		allowed.put(ElementKind.INTERFACE, java.lang.reflect.Modifier.interfaceModifiers());
		allowed.put(ElementKind.LOCAL_VARIABLE, java.lang.reflect.Modifier.FINAL);
		allowed.put(ElementKind.METHOD, java.lang.reflect.Modifier.methodModifiers());
		allowed.put(ElementKind.PARAMETER, java.lang.reflect.Modifier.parameterModifiers());
		allowed.put(ElementKind.STATIC_INIT, java.lang.reflect.Modifier.STATIC);
		ALLOWED = Collections.unmodifiableMap(allowed);
	}

	public static int allowedModifiers(ElementKind kind) {
		Integer allowed = ALLOWED.get(kind);
		return allowed != null ? allowed.intValue() : 0;
	}

	public static Modifiers from(Element element) {
		return new Modifiers().with(element.getModifiers());
	}

	public static int valueOf(Collection<Modifier> modifiers) {
		return modifiers.stream().reduce(0, (v, m) -> v | valueOf(m), (v1, v2) -> v1 | v2);
	}

	public static int valueOf(Element element) {
		return valueOf(element.getModifiers());
	}

	public static int valueOf(Modifier modifier) {
		Integer value = VALUES.get(modifier);
		if (value != null) {
			return value.intValue();
		} else {
			return 0;
		}
	}

	public static int valueOf(Modifier... modifiers) {
		return valueOf(Arrays.asList(modifiers));
	}

	private final Set<Modifier> values;

	public Modifiers() {
		this.values = EnumSet.noneOf(Modifier.class);
	}

	public boolean allowedOn(ElementKind kind) {
		int allowedModifiers = allowedModifiers(kind);
		return ((allowedModifiers & value()) != 0) || values.isEmpty();
	}

	public boolean is(Modifier modifier) {
		return values.contains(modifier);
	}

	public int value() {
		return valueOf(values);
	}

	public Modifiers with(Collection<Modifier> modifiers) {
		values.addAll(modifiers);
		return this;
	}

	public Modifiers with(Element element) {
		return with(element.getModifiers());
	}

	public Modifiers with(Modifier... modifiers) {
		return with(Arrays.asList(modifiers));
	}

	public Modifiers without(Collection<Modifier> modifiers) {
		values.removeAll(modifiers);
		return this;
	}

	public Modifiers without(Element element) {
		return without(element.getModifiers());
	}

	public Modifiers without(Modifier... modifiers) {
		return without(Arrays.asList(modifiers));
	}
}
