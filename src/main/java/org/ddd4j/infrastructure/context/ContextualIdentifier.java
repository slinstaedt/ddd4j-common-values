package org.ddd4j.infrastructure.context;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Type;
import org.ddd4j.value.Value;

public class ContextualIdentifier<T> implements Value<ContextualIdentifier<T>> {

	private final Type<T> type;
	private final String name;

	public ContextualIdentifier(Type<T> type, String name) {
		this.type = Require.nonNull(type);
		this.name = Require.nonEmpty(name);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ContextualIdentifier<?> other = (ContextualIdentifier<?>) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (type == null) {
			if (other.type != null) {
				return false;
			}
		} else if (!type.equals(other.type)) {
			return false;
		}
		return true;
	}

	public String getName() {
		return name;
	}

	public Type<T> getType() {
		return type;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}
}
