package org.ddd4j.schema.avro;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.ddd4j.Throwing;

public enum AvroCoder {
	BINARY {

		@Override
		public Decoder decoder(Schema schema, InputStream in) {
			return DecoderFactory.get().binaryDecoder(in, null);
		}

		@Override
		public Encoder encoder(Schema schema, OutputStream out) {
			return EncoderFactory.get().binaryEncoder(out, null);
		}
	},
	DIRECT {

		@Override
		public Decoder decoder(Schema schema, InputStream in) {
			return DecoderFactory.get().directBinaryDecoder(in, null);
		}

		@Override
		public Encoder encoder(Schema schema, OutputStream out) {
			return EncoderFactory.get().directBinaryEncoder(out, null);
		}
	},
	JSON {

		@Override
		public Decoder decoder(Schema schema, InputStream in) throws IOException {
			return DecoderFactory.get().jsonDecoder(schema, in);
		}

		@Override
		public Encoder encoder(Schema schema, OutputStream out) throws IOException {
			return EncoderFactory.get().jsonEncoder(schema, out);
		}
	},
	JSON_PRETTY {

		@Override
		public Decoder decoder(Schema schema, InputStream in) throws IOException {
			return DecoderFactory.get().jsonDecoder(schema, in);
		}

		@Override
		public Encoder encoder(Schema schema, OutputStream out) throws IOException {
			return EncoderFactory.get().jsonEncoder(schema, out, true);
		}
	};

	public abstract Decoder decoder(Schema schema, InputStream in) throws IOException;

	public abstract Encoder encoder(Schema schema, OutputStream out) throws IOException;

	public Decoder createDecoder(Schema schema, InputStream in) {
		try {
			return decoder(schema, in);
		} catch (IOException e) {
			return Throwing.unchecked(e);
		}
	}

	public Encoder createEncoder(Schema schema, OutputStream out) {
		try {
			return encoder(schema, out);
		} catch (IOException e) {
			return Throwing.unchecked(e);
		}
	}
}
