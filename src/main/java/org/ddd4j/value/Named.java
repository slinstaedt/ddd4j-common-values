package org.ddd4j.value;

public interface Named {

	static String decapitalize(Class<?> type) {
		String result = decapitalize(type.getSimpleName());
		while (type.getEnclosingClass() != null) {
			type = type.getEnclosingClass();
			result = decapitalize(type.getSimpleName()) + "." + result;
		}
		return result;
	}

	static String decapitalize(String name) {
		if (name.isEmpty() || Character.isLowerCase(name.charAt(0))) {
			return name;
		} else {
			return Character.toLowerCase(name.charAt(0)) + name.substring(1);
		}
	}

	default String getName() {
		return decapitalize(getClass().getSimpleName());
	}
}
