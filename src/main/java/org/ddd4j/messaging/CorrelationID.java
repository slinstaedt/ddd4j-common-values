package org.ddd4j.messaging;

import java.io.Serializable;
import java.util.UUID;

public class CorrelationID implements Serializable {

	public static CorrelationID create() {
		return new CorrelationID(UUID.randomUUID());
	}

	private final UUID value;

	public CorrelationID(UUID value) {
		this.value = value;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
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
		CorrelationID other = (CorrelationID) obj;
		if (value == null) {
			if (other.value != null) {
				return false;
			}
		} else if (!value.equals(other.value)) {
			return false;
		}
		return true;
	}
}
