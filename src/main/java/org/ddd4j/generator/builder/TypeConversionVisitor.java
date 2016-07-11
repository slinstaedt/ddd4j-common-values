package org.ddd4j.generator.builder;

import java.util.List;
import java.util.stream.Collectors;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.TypeKindVisitor8;

import com.helger.jcodemodel.AbstractJClass;
import com.helger.jcodemodel.AbstractJType;
import com.helger.jcodemodel.JCodeModel;

public class TypeConversionVisitor<P> extends TypeKindVisitor8<AbstractJType, P> {

	private static final String PACKAGE_INFO_CLASS_NAME = "package-info";

	private JCodeModel model;

	@Override
	public AbstractJType visitArray(ArrayType t, P p) {
		return t.getComponentType().accept(this, p).array();
	}

	@Override
	public AbstractJType visitDeclared(DeclaredType t, P p) {
		AbstractJType jType = t.asElement().asType().accept(this, p);
		if (!t.getTypeArguments().isEmpty()) {
			if (t.asElement() instanceof TypeElement) {
				List<AbstractJClass> typeParameters = ((TypeElement) t.asElement()).getTypeParameters()
						.stream()
						.map(TypeParameterElement::asType)
						.map(m -> m.accept(this, p))
						.map(AbstractJClass.class::cast)
						.collect(Collectors.toList());
				((AbstractJClass) jType).narrow(typeParameters);
			} else {
				throw new AssertionError("Expected " + t.asElement() + " to be a" + TypeElement.class.getName());
			}
		}
		return jType;
	}

	@Override
	public AbstractJType visitError(ErrorType t, P p) {
		return visitDeclared(t, p);
	}

	@Override
	public AbstractJType visitNoTypeAsNone(NoType t, P p) {
		return model.ref(Object.class);
	}

	@Override
	public AbstractJType visitNoTypeAsPackage(NoType t, P p) {
		return model.directClass(PACKAGE_INFO_CLASS_NAME);
	}

	@Override
	public AbstractJType visitNoTypeAsVoid(NoType t, P p) {
		return model.VOID;
	}

	@Override
	public AbstractJType visitNull(NullType t, P p) {
		return model.NULL;
	}

	@Override
	public AbstractJType visitPrimitiveAsBoolean(PrimitiveType t, P p) {
		return model.BOOLEAN;
	}

	@Override
	public AbstractJType visitPrimitiveAsByte(PrimitiveType t, P p) {
		return model.BYTE;
	}

	@Override
	public AbstractJType visitPrimitiveAsChar(PrimitiveType t, P p) {
		return model.CHAR;
	}

	@Override
	public AbstractJType visitPrimitiveAsDouble(PrimitiveType t, P p) {
		return model.DOUBLE;
	}

	@Override
	public AbstractJType visitPrimitiveAsFloat(PrimitiveType t, P p) {
		return model.FLOAT;
	}

	@Override
	public AbstractJType visitPrimitiveAsInt(PrimitiveType t, P p) {
		return model.INT;
	}

	@Override
	public AbstractJType visitPrimitiveAsLong(PrimitiveType t, P p) {
		return model.LONG;
	}

	@Override
	public AbstractJType visitPrimitiveAsShort(PrimitiveType t, P p) {
		return model.SHORT;
	}

	@Override
	public AbstractJType visitTypeVariable(TypeVariable t, P p) {
		AbstractJType jType = t.asElement().asType().accept(this, p);
		// TODO Auto-generated method stub
		return null;
	}
}
