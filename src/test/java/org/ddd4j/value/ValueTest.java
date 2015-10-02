package org.ddd4j.value;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.ddd4j.value.math.Invertible;
import org.ddd4j.value.math.primitive.IntValue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ValueTest {

	@Parameters(name = "{index}: with type {0}")
	public static List<Function<IntValue, Invertible<?>>> data() {
		return Arrays.asList(TestValue::new);
	}

	private final Function<IntValue, Invertible<?>> factory;

	public ValueTest(Function<IntValue, Invertible<?>> factory) {
		this.factory = requireNonNull(factory);
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testBinaryOperation() {
		Invertible o1 = factory.apply(new IntValue(1));
		Invertible o2 = factory.apply(new IntValue(2));

		Invertible o3 = o1.minus(o2);
		assertNotSame(o1, o2);
		assertNotSame(o1, o3);
		assertNotSame(o2, o3);
		assertNotEquals(o1, o2);
		assertNotEquals(o1, o3);
		assertNotEquals(o2, o3);
	}

	@Test
	public void testCreation() {
		Invertible<?> result = factory.apply(IntValue.ZERO);
		assertNotNull(result);
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testEquals() {
		Invertible o1 = factory.apply(new IntValue(1));
		Invertible o2 = factory.apply(new IntValue(2));

		assertEquals(o1.plus(o2), o2.plus(o1));
	}

	@Test
	public void testUnaryOperation() {
		Invertible<?> result = factory.apply(new IntValue(1)).negated();
		assertNotNull(result);
	}
}
