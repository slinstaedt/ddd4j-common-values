package org.ddd4j.test;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.JUnit4;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

public class Dependent extends ParentRunner<Runner> {

	@Retention(RUNTIME)
	@Target(TYPE)
	public @interface DependsOn {

		Class<? extends Runner> runWith() default JUnit4.class;

		Class<?>[] value() default {};
	}

	private final RunnerBuildingContext context;

	public Dependent(Class<?> testClass, RunnerBuilder builder) throws InitializationError {
		super(testClass);
		context = RunnerBuildingContext.of(builder);
		context.analyze(getTestClass().getJavaClass(), getTestClass().getAnnotation(DependsOn.class));
	}

	@Override
	protected void collectInitializationErrors(List<Throwable> errors) {
		super.collectInitializationErrors(errors);
		if (getTestClass().getAnnotation(DependsOn.class) == null) {
			errors.add(new Exception(
					"Test classes run with " + Dependent.class.getName() + " has to be annotated with " + DependsOn.class.getName()));
		}
	}

	@Override
	protected Description describeChild(Runner child) {
		return child.getDescription();
	}

	@Override
	protected List<Runner> getChildren() {
		return context.getChildren();
	}

	@Override
	protected void runChild(Runner child, RunNotifier notifier) {
		child.run(notifier);
	}

	@Override
	public void sort(Sorter sorter) {
		// ignore
	}
}
