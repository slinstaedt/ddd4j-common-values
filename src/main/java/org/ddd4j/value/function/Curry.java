package org.ddd4j.value.function;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.ddd4j.Require;
import org.ddd4j.value.Nothing;
import org.ddd4j.value.Opt;

@FunctionalInterface
public interface Curry<T, C extends Curry<?, ?>> {

	C bind(T param);

	@FunctionalInterface
	interface Query<R> extends Curry<R, Query<R>> {

		@FunctionalInterface
		interface Ed<T, R> extends Curry<T, Query<R>> {

			@Override
			default Query<R> bind(T param) {
				return () -> invoke(param);
			}

			R invoke(T param);
		}

		R evaluate();

		@Override
		default Query<R> bind(R result) {
			return this;
		}

		default R nullSafe() {
			return Require.nonNull(evaluate());
		}

		default Optional<R> asOptional() {
			return Optional.ofNullable(evaluate());
		}
	}

	@FunctionalInterface
	interface Command extends Curry<Nothing, Command> {

		@FunctionalInterface
		interface Ed<T> extends Curry<T, Command> {

			@Override
			default Command bind(T param) {
				return () -> invoke(param);
			}

			void invoke(T param);
		}

		void execute();

		@Override
		default Command bind(Nothing result) {
			return this;
		}
	}

	static void main(String[] args) {
		Curry<String, Query.Ed<Integer, Character>> f1 = s -> s::charAt;
		Query<Character> q = f1.bind("abcd").bind(1);
		System.out.println(q.evaluate());
		System.out.println(skip(f1).bind(2).bind("123").evaluate());

		Command.Ed<List<?>> clear = l -> l.clear();
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
