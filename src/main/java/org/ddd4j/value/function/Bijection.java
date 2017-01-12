package org.ddd4j.value.function;

import java.util.function.Function;

import org.ddd4j.value.collection.Tpl;

@FunctionalInterface
public interface Bijection<L, R> extends Tpl<Function<? super L, ? extends R>, Function<? super R, ? extends L>> {

	static <L, R> Bijection<L, R> of(Function<? super L, ? extends R> left2right, Function<? super R, ? extends L> right2left) {
		return Tpl.of(left2right, right2left)::fold;
	}

	default R applyToLeft(L left) {
		return foldLeft(f -> f.apply(left));
	}

	default L applyToRigth(R right) {
		return foldRight(f -> f.apply(right));
	}
}
