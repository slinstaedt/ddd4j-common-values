package org.ddd4j.test;

import org.ddd4j.test.Dependent.DependsOn;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Dependent.class)
@DependsOn({ Test3.class, Test4.class })
public class Test2 {

	@Test
	public void test() {
	}
}
