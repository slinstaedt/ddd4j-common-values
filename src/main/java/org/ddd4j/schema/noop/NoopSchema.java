package org.ddd4j.schema.noop;

import java.util.Objects;

import org.ddd4j.io.ByteBufferStreamer;
import org.ddd4j.io.Input;
import org.ddd4j.io.Output;
import org.ddd4j.schema.Fingerprint;
import org.ddd4j.schema.Schema;

public class NoopSchema implements Schema<Input> {

	public static final NoopSchema INSTANCE = new NoopSchema();

	private ByteBufferStreamer streamer;

	@Override
	public boolean compatibleWith(Schema<?> existing) {
		return false;
	}

	@Override
	public int hashCode(Object object) {
		return Objects.hashCode(object);
	}

	@Override
	public boolean equal(Object o1, Object o2) {
		return Objects.deepEquals(o1, o2);
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Fingerprint getFingerprint() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Reader<Input> createReader(Input input) {
		return () -> input;
	}

	@Override
	public Writer<Input> createWriter(Output output) {
		return (mode, input) -> mode.apply(input, sink, output);
	}
}
