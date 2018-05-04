package org.ddd4j.value.function;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.ddd4j.util.Require;
import org.ddd4j.value.Nothing;
import org.ddd4j.value.Opt;

@FunctionalInterface
public interface Curry<T, C extends Curry<?, ?>> {

	C bind(T param);

	@FunctionalInterface
	interface Query<T, R> extends Curry<T, Query.Term<R>> {

		@FunctionalInterface
		interface Term<R> extends Curry<R, Term<R>> {

			@Override
			default Term<R> bind(R result) {
				return this;
			}

			R evaluate();

			default R nullSafe() {
				return Require.nonNull(evaluate());
			}

			default Optional<R> asOptional() {
				return Optional.ofNullable(evaluate());
			}
		}

		@Override
		default Term<R> bind(T param) {
			return () -> evaluate(param);
		}

		R evaluate(T param);
	}

	@FunctionalInterface
	interface Command<T> extends Curry<T, Command.Term> {

		@FunctionalInterface
		interface Term extends Curry<Nothing, Term> {

			@FunctionalInterface
			interface Ed<T> extends Curry<T, Term> {

				@Override
				default Term bind(T param) {
					return () -> invoke(param);
				}

				void invoke(T param);
			}

			void execute();

			@Override
			default Term bind(Nothing result) {
				return this;
			}
		}

		@Override
		default Term bind(T param) {
			return () -> invoke(param);
		}

		void invoke(T param);
	}

	static void main(String[] args) {
		Curry<String, Query<Integer, Character>> f1 = s -> s::charAt;
		Query.Term<Character> q = f1.bind("abcd").bind(1);
		System.out.println(q.evaluate());
		System.out.println(skip(f1).bind(2).bind("123").evaluate());

		Command<List<?>> clear = l -> l.clear();
		List<Integer> asList = Arrays.asList(1, 2, 3);
		clear.bind(asList).execute();
		System.out.println(asList);
		skip(clear);
	}

	static <T, S, C extends Curry<?, ?>> Curry<S, Curry<T, C>> skip(Curry<T, ? extends Curry<S, C>> curried) {
		return s -> t -> curried.bind(t).bind(s);
	}

	default Curry<Opt<T>, C> withDefault(T defaultArgument) {
		return o -> bind(o.orElse(defaultArgument));
	}
}
