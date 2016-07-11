package org.ddd4j.generator.builder;

import static java.util.Objects.requireNonNull;

import com.helger.jcodemodel.JCodeModel;
import com.helger.jcodemodel.JMethod;

public class MethodBuilder {

	private final JCodeModel model;
	private final JMethod jConstructor;

	MethodBuilder(JCodeModel model, JMethod jConstructor) {
		this.model = requireNonNull(model);
		this.jConstructor = requireNonNull(jConstructor);
	}
}
