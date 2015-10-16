package org.ddd4j.value;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.collection.Tpl;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SeqTest<T, U> {

	public static class Case<T, U> {

		public class BinaryOperation<R> {

			private final BiFunction<T, U, R> function;
			private final Predicate<? super R> expected;

			public BinaryOperation(BiFunction<T, U, R> function, Predicate<? super R> expected) {
				this.function = requireNonNull(function);
				this.expected = requireNonNull(expected);
			}

			public void run(T t, U u) {
				R result = function.apply(t, u);
				boolean outcome = expected.test(result);
				Assert.assertTrue(outcome);
			}
		}

		public class UnaryOperation<R> {

			private final Function<T, R> function;
			private final Predicate<? super R> expected;

			public UnaryOperation(Function<T, R> function, Predicate<? super R> expected) {
				this.function = requireNonNull(function);
				this.expected = requireNonNull(expected);
			}

			public void run(T t) {
				R result = function.apply(t);
				boolean outcome = expected.test(result);
				Assert.assertTrue(outcome);
			}
		}

		private final T t;
		private final U u;
		private final List<UnaryOperation<?>> unary;
		private final List<BinaryOperation<?>> binary;

		public Case(T t, U u) {
			this.t = t;
			this.u = u;
			this.unary = new ArrayList<>();
			this.binary = new ArrayList<>();
		}

		public <R> void run() {
			for (UnaryOperation<?> operation : unary) {
				operation.run(t);
			}
			for (BinaryOperation<?> operation : binary) {
				operation.run(t, u);
			}
		}

		public <R> Case<T, U> test(BiFunction<T, U, R> function, Predicate<? super R> expected) {
			binary.add(new BinaryOperation<>(function, expected));
			return this;
		}

		public <R> Case<T, U> test(Function<T, R> function, Predicate<? super R> expected) {
			unary.add(new UnaryOperation<>(function, expected));
			return this;
		}
	}

	@Parameters
	public static Object[] data() {
		List<Case<?, ?>> cases = new ArrayList<>();
		cases.add(new Case<>(Seq.of(1, 2, 3, 4), Seq.of(3, 4, 5, 6))//
		.test((t, u) -> t.join().inner(u), Seq.of(Tpl.of(3, 3), Tpl.of(4, 4))::equal)
				.test((t, u) -> t.join().left(u), Seq.of(Tpl.of(3, 3), Tpl.of(4, 4))::equal)
				.test((t, u) -> t.join().right(u), Seq.of(Tpl.of(3, 3), Tpl.of(4, 4))::equal)
				.test((t, u) -> t.join().outer(u), Seq.of(Tpl.of(3, 3), Tpl.of(4, 4))::equal));
		return cases.stream().map(c -> new Case<?, ?>[] { c }).toArray();
	}

	private final Case<T, U> testCase;

	public SeqTest(Case<T, U> testCase) {
		this.testCase = requireNonNull(testCase);
	}

	@Test
	public void testJoin() {
		testCase.run();
	}
}
