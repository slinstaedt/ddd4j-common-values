package org.ddd4j.test;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Test3 {

	@Parameters
	public static List<Integer> params() {
		return Arrays.asList(1, 2, 3, 4, 5);
	}

	private final int param;

	public Test3(int param) {
		this.param = param;
	}

	@Test
	public void test() {
		Assert.assertNotEquals(3, param);
	}
}
