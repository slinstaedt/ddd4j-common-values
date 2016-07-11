package org.ddd4j.generator;

import java.util.stream.Stream;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;

public interface CodeGenerationVisitor extends ElementVisitor<CodeGenerationVisitor, Context> {

	default Stream<Element> streamDirectChildren(Element element) {
		return element.getEnclosedElements().stream().map(Element.class::cast);
	}

	default Stream<Element> streamDirectChildrenAndSelf(Element element) {
		return Stream.concat(Stream.of(element), streamDirectChildren(element));
	}

	default Stream<Element> streamRecursiveChildren(Element element) {
		return Stream.concat(streamDirectChildren(element),
				streamDirectChildren(element).flatMap(this::streamRecursiveChildren));
	}

	default Stream<Element> streamRecursiveChildrenAndSelf(Element element) {
		return Stream.concat(Stream.of(element), streamRecursiveChildren(element));
	}
}
