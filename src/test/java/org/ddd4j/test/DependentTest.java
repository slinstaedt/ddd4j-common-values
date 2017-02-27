package org.ddd4j.test;

import org.ddd4j.test.Dependent.DependsOn;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Dependent.class)
@DependsOn({ Test1.class, Test2.class })
public class DependentTest {

	@Test
	public void test() {
		System.out.println(getClass().getSimpleName());
	}
}
