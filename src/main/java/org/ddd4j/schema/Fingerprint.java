package org.ddd4j.schema;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.Hash;

public class Fingerprint extends Hash<Schema<?>> {

	public static Fingerprint deserialize(ReadBuffer buffer) {
		return new Fingerprint(buffer.getBytes());
	}

	public Fingerprint(byte[] value) {
		super(value);
	}
}
