package org.ddd4j.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.ddd4j.test.Dependent.DependsOn;
import org.junit.internal.builders.AnnotatedBuilder;
import org.junit.internal.runners.ErrorReportingRunner;
import org.junit.runner.Runner;
import org.junit.runners.JUnit4;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

public class RunnerBuildingContext extends RunnerBuilder {

	private interface RunnerFactory {

		Runner create(Class<?> testClass) throws Exception;

		default Runner safeCreate(Class<?> testClass) throws Exception {
			Runner runner = create(testClass);
			if (runner == null) {
				runner = new JUnit4(testClass);
			}
			return runner;
		}
	}

	public static RunnerBuildingContext of(RunnerBuilder candidate) {
		if (candidate instanceof RunnerBuildingContext) {
			return (RunnerBuildingContext) candidate;
		} else {
			return new RunnerBuildingContext();
		}
	}

	private final Map<Class<?>, ResultCollectingRunner> runners;
	private final AnnotatedBuilder delegate;

	private RunnerBuildingContext() {
		this.runners = new ConcurrentHashMap<>();
		this.delegate = new AnnotatedBuilder(this);
	}

	public void analyze(Class<?> testClass, DependsOn dependsOn) throws InitializationError {
		ResultCollectingRunner runner = resultCollectingRunner(testClass, c -> delegate.buildRunner(dependsOn.runWith(), c));
		runner.addDependencies(runners(testClass, dependsOn.value()));
	}

	public List<Runner> getChildren() {
		return new ArrayList<>(new TreeSet<>(runners.values()));
	}

	private ResultCollectingRunner resultCollectingRunner(Class<?> testClass, RunnerFactory factory) throws InitializationError {
		ResultCollectingRunner runner = runners.get(testClass);
		if (runner == null) {
			try {
				runner = new ResultCollectingRunner(factory.safeCreate(testClass));
			} catch (Exception e) {
				runner = new ResultCollectingRunner(new ErrorReportingRunner(testClass, e));
			}
			if (runners.putIfAbsent(testClass, runner) != null) {
				runner = runners.get(testClass);
			}
		}
		return runner;
	}

	@Override
	public ResultCollectingRunner runnerForClass(Class<?> testClass) throws Throwable {
		return resultCollectingRunner(testClass, delegate::runnerForClass);
	}
}
