package org.ddd4j.value;

public interface Named {

	static String decapitalize(String name) {
		if (name.isEmpty() || Character.isLowerCase(name.charAt(0))) {
			return name;
		} else {
			return Character.toLowerCase(name.charAt(0)) + name.substring(1);
		}
	}

	default String name() {
		return decapitalize(getClass().getTypeName());
	}
}
