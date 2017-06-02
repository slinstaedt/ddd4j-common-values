package org.ddd4j.schema.avro;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;

public enum AvroCoder {

	BINARY {

		@Override
		public Decoder decoder(DecoderFactory factory, Schema schema, InputStream in) {
			return factory.binaryDecoder(in, null);
		}

		@Override
		public Encoder encoder(EncoderFactory factory, Schema schema, OutputStream out) {
			return factory.binaryEncoder(out, null);
		}
	},
	DIRECT {

		@Override
		public Decoder decoder(DecoderFactory factory, Schema schema, InputStream in) {
			return factory.directBinaryDecoder(in, null);
		}

		@Override
		public Encoder encoder(EncoderFactory factory, Schema schema, OutputStream out) {
			return factory.directBinaryEncoder(out, null);
		}
	},
	JSON {

		@Override
		public Decoder decoder(DecoderFactory factory, Schema schema, InputStream in) throws IOException {
			return factory.jsonDecoder(schema, in);
		}

		@Override
		public Encoder encoder(EncoderFactory factory, Schema schema, OutputStream out) throws IOException {
			return factory.jsonEncoder(schema, out);
		}
	},
	JSON_PRETTY {

		@Override
		public Decoder decoder(DecoderFactory factory, Schema schema, InputStream in) throws IOException {
			return factory.jsonDecoder(schema, in);
		}

		@Override
		public Encoder encoder(EncoderFactory factory, Schema schema, OutputStream out) throws IOException {
			return factory.jsonEncoder(schema, out, true);
		}
	};

	public abstract Decoder decoder(DecoderFactory factory, Schema schema, InputStream in) throws IOException;

	public abstract Encoder encoder(EncoderFactory factory, Schema schema, OutputStream out) throws IOException;
}
