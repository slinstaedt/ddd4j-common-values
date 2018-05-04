package org.ddd4j.schema.avro;

import java.security.NoSuchAlgorithmException;

import org.apache.avro.SchemaNormalization;
import org.ddd4j.schema.Fingerprint;
import org.ddd4j.util.Require;

public enum AvroFingerprintAlgorithm {
	CRC_64_AVRO("CRC-64-AVRO"), MD5("MD5"), SHA_256("SHA-256");

	private final String value;

	AvroFingerprintAlgorithm(String value) {
		this.value = Require.nonEmpty(value);
	}

	Fingerprint parsingFingerprint(org.apache.avro.Schema schema) {
		try {
			return new Fingerprint(SchemaNormalization.parsingFingerprint(value, schema));
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Could not create fingerprint with algorithm " + value, e);
		}
	}
}