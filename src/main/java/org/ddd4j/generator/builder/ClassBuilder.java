package org.ddd4j.generator.builder;

import static java.util.Objects.requireNonNull;

import javax.lang.model.type.TypeMirror;

import org.ddd4j.generator.Modifiers;

import com.helger.jcodemodel.AbstractJType;
import com.helger.jcodemodel.JCodeModel;
import com.helger.jcodemodel.JDefinedClass;
import com.helger.jcodemodel.JFieldVar;
import com.helger.jcodemodel.JMethod;

public class ClassBuilder {

	private final JCodeModel model;
	private final JDefinedClass jClass;
	private final TypeConversionVisitor<Void> typeVisitor;

	ClassBuilder(JCodeModel model, JDefinedClass jClass) {
		this.model = requireNonNull(model);
		this.jClass = requireNonNull(jClass);
		this.typeVisitor = new TypeConversionVisitor<Void>();
	}

	public MethodBuilder withConstructor(Modifiers modifiers) {
		JMethod jConstructor = jClass.constructor(modifiers.value());
		return new MethodBuilder(model, jConstructor);
	}

	public FieldBuilder withField(Modifiers modifiers, Class<?> fieldType, String name) {
		AbstractJType jType = model._ref(fieldType);
		JFieldVar jField = jClass.field(modifiers.value(), jType, name);
		return new FieldBuilder(model, jField);
	}

	public FieldBuilder withField(Modifiers modifiers, TypeMirror fieldType, String name) {
		AbstractJType jType = typeVisitor.visit(fieldType);
		JFieldVar jField = jClass.field(modifiers.value(), jType, name);
		return new FieldBuilder(model, jField);
	}

	public MethodBuilder withMethod(Modifiers modifiers, Class<?> returnType, String name) {
		AbstractJType jType = model._ref(returnType);
		JMethod jMethod = jClass.method(modifiers.value(), jType, name);
		return new MethodBuilder(model, jMethod);
	}

	public MethodBuilder withMethod(Modifiers modifiers, TypeMirror returnType, String name) {
		AbstractJType jType = typeVisitor.visit(returnType);
		JMethod jMethod = jClass.method(modifiers.value(), jType, name);
		return new MethodBuilder(model, jMethod);
	}
}
