package org.ddd4j.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.ddd4j.Require;
import org.ddd4j.value.config.Configuration;

public class RadixTree<T> implements Configuration {

	public static void main(String[] args) {
		RadixTree<String> tree = new RadixTree<>();
		tree.put("hallo", "welt!");
		tree.put("hallowelt", "!");
		tree.put("hal", "lowelt!");
		tree.put("HALLO", "WELT!");
		System.out.println(tree.get("hallo"));
		System.out.println(Arrays.toString(tree.getPrefixed("hallo").toArray()));
		System.out.println(Arrays.toString(tree.getAll().toArray()));
	}

	private static CharSequence sliceFrom(CharSequence s, int start) {
		return s.subSequence(start, s.length());
	}

	private static CharSequence sliceUntil(CharSequence s, int end) {
		return s.subSequence(0, end);
	}

	private final Map<CharSequence, RadixTree<T>> children;
	private RadixTree<T> parent;
	private CharSequence key;
	private T value;

	public RadixTree() {
		this.children = new HashMap<>();
		this.key = "";
	}

	private RadixTree<T> child(CharSequence key, boolean create) {
		if (key.length() > 0) {
			return lookup(key) //
					.map(c -> c.child(sliceFrom(key, c.key.length()), create))
					.or(() -> childStartingWith(key, create))
					.orElseGet(() -> create ? newChild(key) : this);
		} else {
			return this;
		}
	}

	private Optional<RadixTree<T>> childStartingWith(CharSequence candidate, boolean split) {
		RadixTree<T> result = null;
		for (RadixTree<T> child : children.values()) {
			int matching = 0;
			int max = Math.min(child.key.length(), candidate.length());
			while (matching < max && child.key.charAt(matching) == candidate.charAt(matching)) {
				matching++;
			}
			if (child.key.length() > matching && matching > 0) {
				result = split ? child.split(matching) : child;
				break;
			}
		}
		return Optional.ofNullable(result);
	}

	public Optional<T> get(CharSequence key) {
		if (key.length() > 0) {
			return lookup(key).flatMap(c -> c.get(sliceFrom(key, c.key.length())));
		} else {
			return Optional.ofNullable(value);
		}
	}

	public Stream<T> getAll() {
		Stream<T> stream = value != null ? Stream.of(value) : Stream.empty();
		return children.values().stream().reduce(stream, (s, t) -> Stream.concat(s, t.getAll()), Stream::concat);
	}

	public Stream<T> getPrefixed(CharSequence prefix) {
		return child(prefix, false).getAll();
	}

	@Override
	public Optional<String> getString(String key) {
		return get(key).map(Object::toString);
	}

	private Optional<RadixTree<T>> lookup(CharSequence key) {
		return children.keySet()
				.stream()
				.mapToInt(CharSequence::length)
				.distinct()
				.filter(l -> l <= key.length())
				.mapToObj(l -> sliceUntil(key, l))
				.map(children::get)
				.filter(Objects::nonNull)
				.findFirst();
	}

	private RadixTree<T> newChild(CharSequence key) {
		RadixTree<T> child = new RadixTree<>();
		relocateChild(key, child);
		return child;
	}

	public void put(CharSequence key, T value) {
		Require.nonNull(value);
		child(key, true).value = value;
	}

	private void relocateChild(CharSequence key, RadixTree<T> child) {
		if (child.parent != null && child.key != null) {
			child.parent.children.remove(child.key);
		}
		child.key = key;
		child.parent = this;
		this.children.put(child.key, child);
	}

	private RadixTree<T> split(int matching) {
		RadixTree<T> intermediate = parent.newChild(sliceUntil(key, matching));
		intermediate.relocateChild(sliceFrom(key, matching), this);
		return intermediate;
	}
}
