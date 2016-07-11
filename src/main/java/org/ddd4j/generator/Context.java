package org.ddd4j.generator;

import static java.util.Objects.requireNonNull;

import javax.annotation.processing.ProcessingEnvironment;

public class Context {

	private final ProcessingEnvironment processingEnv;

	public Context(ProcessingEnvironment processingEnv) {
		this.processingEnv = requireNonNull(processingEnv);
	}
}
