package org.ddd4j.io;

public interface ByteOrder {

	public interface IndexedBytes {

		byte get(int index);

		IndexedBytes put(int index, byte b);
	}

	ByteOrder BIG_ENDIAN = new ByteOrder() {

		@Override
		public char getChar(IndexedBytes bytes, int index) {
			return (char) ((bytes.get(index + 1) & 0xFF) + (bytes.get(index) << 8));
		}

		@Override
		public double getDouble(IndexedBytes bytes, int index) {
			return Double.longBitsToDouble(getLong(bytes, index));
		}

		@Override
		public float getFloat(IndexedBytes bytes, int index) {
			return Float.intBitsToFloat(getInt(bytes, index));
		}

		@Override
		public int getInt(IndexedBytes bytes, int index) {
			return ((bytes.get(index + 3) & 0xFF)) //
					+ ((bytes.get(index + 2) & 0xFF) << 8) //
					+ ((bytes.get(index + 1) & 0xFF) << 16) //
					+ ((bytes.get(index)) << 24);
		}

		@Override
		public long getLong(IndexedBytes bytes, int index) {
			return ((bytes.get(index + 7) & 0xFFL)) //
					+ ((bytes.get(index + 6) & 0xFFL) << 8) //
					+ ((bytes.get(index + 5) & 0xFFL) << 16) //
					+ ((bytes.get(index + 4) & 0xFFL) << 24) //
					+ ((bytes.get(index + 3) & 0xFFL) << 32) //
					+ ((bytes.get(index + 2) & 0xFFL) << 40) //
					+ ((bytes.get(index + 1) & 0xFFL) << 48) //
					+ (((long) bytes.get(index + 0)) << 56);
		}

		@Override
		public short getShort(IndexedBytes bytes, int index) {
			return (short) ((bytes.get(index + 1) & 0xFF) + (bytes.get(index) << 8));
		}

		@Override
		public void putChar(IndexedBytes bytes, int index, char value) {
			bytes.put(index + 1, (byte) value);
			bytes.put(index + 0, (byte) (value >>> 8));
		}

		@Override
		public void putDouble(IndexedBytes bytes, int index, double value) {
			putLong(bytes, index, Double.doubleToLongBits(value));
		}

		@Override
		public void putFloat(IndexedBytes bytes, int index, float value) {
			putInt(bytes, index, Float.floatToIntBits(value));
		}

		@Override
		public void putInt(IndexedBytes bytes, int index, int value) {
			bytes.put(index + 3, (byte) value);
			bytes.put(index + 2, (byte) (value >>> 8));
			bytes.put(index + 1, (byte) (value >>> 16));
			bytes.put(index + 0, (byte) (value >>> 24));
		}

		@Override
		public void putLong(IndexedBytes bytes, int index, long value) {
			bytes.put(index + 7, (byte) value);
			bytes.put(index + 6, (byte) (value >>> 8));
			bytes.put(index + 5, (byte) (value >>> 16));
			bytes.put(index + 4, (byte) (value >>> 24));
			bytes.put(index + 3, (byte) (value >>> 32));
			bytes.put(index + 2, (byte) (value >>> 40));
			bytes.put(index + 1, (byte) (value >>> 48));
			bytes.put(index + 0, (byte) (value >>> 56));
		}

		@Override
		public void putShort(IndexedBytes bytes, int index, short value) {
			bytes.put(index + 1, (byte) value);
			bytes.put(index + 0, (byte) (value >>> 8));
		}
	};

	byte BOOLEAN_FALSE_ENCODING = 0;

	public default boolean getBoolean(IndexedBytes bytes, int index) {
		return bytes.get(index) != BOOLEAN_FALSE_ENCODING;
	}

	char getChar(IndexedBytes bytes, int index);

	double getDouble(IndexedBytes bytes, int index);

	float getFloat(IndexedBytes bytes, int index);

	int getInt(IndexedBytes bytes, int index);

	long getLong(IndexedBytes bytes, int index);

	short getShort(IndexedBytes bytes, int index);

	default void putBoolean(IndexedBytes bytes, int index, boolean value) {
		bytes.put(index, value ? ~BOOLEAN_FALSE_ENCODING : BOOLEAN_FALSE_ENCODING);
	}

	void putChar(IndexedBytes bytes, int index, char value);

	void putDouble(IndexedBytes bytes, int index, double value);

	void putFloat(IndexedBytes bytes, int index, float value);

	void putInt(IndexedBytes bytes, int index, int value);

	void putLong(IndexedBytes bytes, int index, long value);

	void putShort(IndexedBytes bytes, int index, short value);
}
