package org.ddd4j.generator.builder;

import static java.util.Objects.requireNonNull;

import com.helger.jcodemodel.JCodeModel;
import com.helger.jcodemodel.JFieldVar;

public class FieldBuilder {

	private final JCodeModel model;
	private final JFieldVar jField;

	FieldBuilder(JCodeModel model, JFieldVar jField) {
		this.model = requireNonNull(model);
		this.jField = requireNonNull(jField);
	}
}
