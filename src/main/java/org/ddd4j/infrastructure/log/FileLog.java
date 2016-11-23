package org.ddd4j.infrastructure.log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.ddd4j.stream.Broadcast;
import org.reactivestreams.Publisher;

public class FileLog implements Log<byte[]>, Runnable {

	private File file;

	private CompletionStage<List<Record<byte[]>>> load(FileChannel channel, long n) {
	}

	@Override
	public Publisher<Record<byte[]>> publisher(Offset initialOffset, boolean completeOnEnd) throws IOException {
		FileInputStream stream = new FileInputStream(file);
		FileChannel channel = stream.getChannel().position(initialOffset.getValue());
		return Broadcast.create(n -> load(channel, n), stream);
	}

	@Override
	public CompletionStage<Record<byte[]>> tryAppend(Commit<byte[]> commit) {
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
	}
}
