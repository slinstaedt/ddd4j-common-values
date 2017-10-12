package org.ddd4j.value.versioned;

public enum Position {
	AHEAD, UPTODATE, BEHIND, FAILED;

	public static Position of(int comparison) {
		switch (Integer.signum(comparison)) {
		case -1:
			return Position.BEHIND;
		case 0:
			return Position.UPTODATE;
		case 1:
			return Position.AHEAD;
		default:
			return Position.FAILED;
		}
	}
}