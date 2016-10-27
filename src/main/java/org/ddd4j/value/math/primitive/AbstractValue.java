package org.ddd4j.value.math.primitive;

public abstract class AbstractValue {

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
		AbstractValue other = (AbstractValue) obj;
		if (value() == null) {
			if (other.value() != null) {
				return false;
			}
		} else if (!value().equals(other.value())) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((value() == null) ? 0 : value().hashCode());
		return result;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + value() + "]";
	}

	protected abstract Object value();
}
