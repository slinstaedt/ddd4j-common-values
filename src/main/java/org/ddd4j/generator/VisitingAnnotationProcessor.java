package org.ddd4j.generator;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.Completion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

public class VisitingAnnotationProcessor implements Processor {

	private static <A extends Annotation> Stream<A> fetch(Iterable<?> objects, Class<A> type) {
		Set<A> annotations = new HashSet<>();
		for (Object object : objects) {
			annotations.addAll(Arrays.asList(object.getClass().getAnnotationsByType(type)));
		}
		return annotations.stream();
	}

	private static <A extends Annotation, T> Set<T> fetch(Iterable<?> objects, Class<A> type, Function<A, T[]> mapper) {
		return fetch(objects, type).flatMap(a -> Arrays.asList(mapper.apply(a)).stream()).collect(Collectors.toSet());
	}

	private final ServiceLoader<CodeGenerationVisitor> visitors;
	private final Set<String> supportedOptions;
	private final Set<String> supportedAnnotationTypes;
	private Context context;

	public VisitingAnnotationProcessor() {
		this.visitors = ServiceLoader.load(CodeGenerationVisitor.class);
		this.supportedOptions = fetch(visitors, SupportedOptions.class, SupportedOptions::value);
		this.supportedAnnotationTypes = fetch(visitors, SupportedAnnotationTypes.class, SupportedAnnotationTypes::value);
	}

	@Override
	public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation,
			ExecutableElement member, String userText) {
		return Collections.emptyList();
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return Collections.unmodifiableSet(supportedAnnotationTypes);
	}

	@Override
	public Set<String> getSupportedOptions() {
		return Collections.unmodifiableSet(supportedOptions);
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@Override
	public void init(ProcessingEnvironment processingEnv) {
		context = new Context(processingEnv);
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
		for (CodeGenerationVisitor visitor : visitors) {
			for (TypeElement annotation : annotations) {
				Set<? extends Element> elements = env.getElementsAnnotatedWith(annotation);
				for (Element element : elements) {
					element.accept(visitor, context);
				}
			}
			annotations.stream()
					.flatMap(te -> env.getElementsAnnotatedWith(te).stream())
					.forEach(e -> e.accept(visitor, context));
		}
		return false;
	}
}
