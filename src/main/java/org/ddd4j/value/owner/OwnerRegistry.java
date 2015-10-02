package org.ddd4j.value.owner;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import org.ddd4j.value.owner.XXX.Owned;

public class OwnerRegistry {

	private final Map<Object, Owned<?>> items;

	public OwnerRegistry() {
		this(WeakHashMap::new);
	}

	public OwnerRegistry(Supplier<Map<Object, Owned<?>>> mapSupplier) {
		this.items = mapSupplier.get();
	}

	public boolean isOwned(Object item) {
		return items.containsKey(item);
	}

	public <T> Owned<T> own(T item, Object owner) {
		Property<T> owned = property(item);

	}

	@SuppressWarnings("unchecked")
	public <T> Property<T> property(T item) {
		Owned<?> owned = items.get(item);
		if (owned != null) {
		} else {
			return XXX.shared(item);
		}
	}

	public <T> void share(T item) {
	}
}
