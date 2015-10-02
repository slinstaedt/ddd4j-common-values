package org.ddd4j.value.math.primitive;

import org.ddd4j.value.Value;
import org.ddd4j.value.math.Binary;
import org.ddd4j.value.math.Invertible;

public final class IntValue extends Value implements Invertible<IntValue>, Binary<IntValue> {

	public static final IntValue ZERO = new IntValue(0);

	private final int value;

	public IntValue(int value) {
		this.value = value;
	}

	@Override
	public IntValue and(IntValue other) {
		return new IntValue(value & other.value);
	}

	@Override
	public IntValue flip(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean get(int index) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public IntValue leftLogicalShift(int shift) {
		return new IntValue(value << shift);
	}

	@Override
	public IntValue negated() {
		return new IntValue(-value);
	}

	@Override
	public IntValue not() {
		return new IntValue(~value);
	}

	@Override
	public IntValue or(IntValue other) {
		return new IntValue(value | other.value);
	}

	@Override
	public IntValue plus(IntValue other) {
		return new IntValue(value + other.value);
	}

	@Override
	public IntValue rightArithmeticShift(int shift) {
		return new IntValue(value >>> shift);
	}

	@Override
	public IntValue rightLogicalShift(int shift) {
		return new IntValue(value >> shift);
	}

	@Override
	public IntValue self() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IntValue set(int index, boolean bit) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Sign sign() {
		return Sign.of(value);
	}

	@Override
	protected Object value() {
		return value;
	}

	@Override
	public IntValue xor(IntValue other) {
		return new IntValue(value ^ other.value);
	}
}
