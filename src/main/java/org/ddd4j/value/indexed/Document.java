package org.ddd4j.value.indexed;

import java.util.List;
import java.util.function.Function;

import org.ddd4j.value.versioned.Revision;

public class Document<T> {

	public class Field<V> {

		private Function<? super T, V> mapping;
		private boolean index;
		private boolean store;
	}

	private List<Field<?>> fields;

	public <X extends Comparable<X>> Field<X> withComparableField(String name, Function<? super T, X> mapping) {
		Field<X> field = new Field<>();
		fields.add(field);
		return field;
	}

	public Field<String> withFulltextField(String name, Function<? super T, String> mapping) {
		Field<String> field = new Field<>();
		fields.add(field);
		return field;
	}

	public Field<Integer> withIntegerField(String name, Function<? super T, Integer> mapping) {
		Field<Integer> field = new Field<>();
		fields.add(field);
		return field;
	}

	public Field<Long> withLongField(String name, Function<? super T, Long> mapping) {
		Field<Long> field = new Field<>();
		fields.add(field);
		return field;
	}

	public Document<T> withRevisionField(Function<? super T, Revision> mapping) {
		Field<Revision> field = new Field<>();
		fields.add(field);
		return this;
	}

	public Field<String> withStringField(String name, Function<? super T, String> mapping) {
		Field<String> field = new Field<>();
		fields.add(field);
		return field;
	}
}
