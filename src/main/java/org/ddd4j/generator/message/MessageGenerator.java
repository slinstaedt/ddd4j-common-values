package org.ddd4j.generator.message;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementKindVisitor8;

import org.ddd4j.generator.CodeGenerationVisitor;
import org.ddd4j.generator.Context;
import org.ddd4j.generator.Modifiers;

public class MessageGenerator extends ElementKindVisitor8<CodeGenerationVisitor, Context> implements CodeGenerationVisitor {

	@Override
	public CodeGenerationVisitor visitExecutableAsMethod(ExecutableElement e, Context ctx) {
		if (Modifiers.from(e).is(Modifier.STATIC)) {
			return super.visitExecutableAsMethod(e, ctx);
		}

		return super.visitExecutableAsMethod(e, ctx);
	}

	@Override
	public CodeGenerationVisitor visitTypeAsInterface(TypeElement element, Context ctx) {
		streamRecursiveChildrenAndSelf(element).anyMatch(e -> e.getAnnotation(Deprecated.class) != null);

		return this;
	}
}
