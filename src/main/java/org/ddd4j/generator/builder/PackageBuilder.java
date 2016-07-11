package org.ddd4j.generator.builder;

import static java.util.Objects.requireNonNull;

import org.ddd4j.generator.Modifiers;
import org.ddd4j.value.Throwing;

import com.helger.jcodemodel.EClassType;
import com.helger.jcodemodel.JClassAlreadyExistsException;
import com.helger.jcodemodel.JCodeModel;
import com.helger.jcodemodel.JDefinedClass;
import com.helger.jcodemodel.JPackage;

public class PackageBuilder {

	private final JCodeModel model;
	private final JPackage jPackage;

	PackageBuilder(JCodeModel model, JPackage jPackage) {
		this.model = requireNonNull(model);
		this.jPackage = requireNonNull(jPackage);
	}

	private ClassBuilder create(Modifiers modifiers, String name, EClassType type) {
		try {
			JDefinedClass jClass = jPackage._class(modifiers.value(), name, type);
			return new ClassBuilder(model, jClass);
		} catch (JClassAlreadyExistsException e) {
			return Throwing.unchecked(e);
		}
	}

	public ClassBuilder withAnnotation(Modifiers modifiers, String name) {
		return create(modifiers, name, EClassType.ANNOTATION_TYPE_DECL);
	}

	public ClassBuilder withClass(Modifiers modifiers, String name) {
		return create(modifiers, name, EClassType.CLASS);
	}

	public ClassBuilder withEnum(Modifiers modifiers, String name) {
		return create(modifiers, name, EClassType.ENUM);
	}

	public ClassBuilder withInterface(Modifiers modifiers, String name) {
		return create(modifiers, name, EClassType.INTERFACE);
	}
}
