package org.ddd4j.function;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

public class Fibonacci extends RecursiveTask<Integer> {

	private static final long serialVersionUID = 1L;

	public static void main(String[] args) {
		ForkJoinPool pool = ForkJoinPool.commonPool();
		for (int i = 0; i < 45; i++) {
			Integer result = pool.invoke(new Fibonacci(i));
			System.out.println(result);
		}
	}

	final int n;

	public Fibonacci(int n) {
		this.n = n;
	}

	@Override
	protected Integer compute() {
		if (n <= 1) {
			return n;
		}
		ForkJoinTask<Integer> f1 = new Fibonacci(n - 1).fork();
		return new Fibonacci(n - 2).compute() + f1.join();
	}
}