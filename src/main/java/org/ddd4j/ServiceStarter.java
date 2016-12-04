package org.ddd4j;

import java.util.Arrays;

import org.ddd4j.infrastructure.queue.Queue;
import org.ddd4j.infrastructure.queue.Queue.Consumer;
import org.ddd4j.infrastructure.queue.QueueFactory;
import org.ddd4j.spi.Providers;

public class ServiceStarter {

	public static void main(String[] args) {
		Queue<Integer> queue = Providers.createServiceLocator().locate(QueueFactory.class).create();
		System.out.println(queue.getBufferSize());
		Consumer<Integer> consumer = queue.consumer();
		queue.producer().publishAll(Arrays.asList(1, 2, 3));
		consumer.consumeAll(System.out::println);
	}
}
