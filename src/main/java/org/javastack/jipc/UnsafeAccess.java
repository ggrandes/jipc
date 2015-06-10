package org.javastack.jipc;

import java.lang.reflect.Field;
import java.nio.Buffer;

import sun.misc.Unsafe;

/**
 * Access to sun.misc.Unsafe (subset)
 * <a href="http://j7a.ru/classsun_1_1misc_1_1_unsafe.html">sun.misc.Unsafe API</a>
 */
@SuppressWarnings("restriction")
class UnsafeAccess {
	private static final Unsafe UNSAFE;
	private static final long bufferAddressOffset;
	static {
		try {
			// Get Access to sun.misc.Unsafe
			final Field field = Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			UNSAFE = (Unsafe) field.get(null);
			// Get direct access to Buffer.address
			bufferAddressOffset = UNSAFE.objectFieldOffset(Buffer.class.getDeclaredField("address"));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean isUnsafeAvailable() {
		return (UNSAFE != null);
	}

	/**
	 * Atomically update Java variable to x if it is currently holding expected.
	 * 
	 * @param obj
	 * @param valueOffset
	 * @param expect
	 * @param update
	 * @return true if success
	 */
	public static final boolean compareAndSwapInt(final Object obj, final long valueOffset, final int expect,
			final int update) {
		return UNSAFE.compareAndSwapInt(obj, valueOffset, expect, update);
	}

	/**
	 * Fetches a value from a given memory address.
	 * 
	 * @param address
	 * @return
	 */
	public static byte getByte(final long address) {
		return UNSAFE.getByte(address);
	}

	/**
	 * Stores a value into a given memory address.
	 * 
	 * @param address
	 * @param value
	 */
	public static void putByte(final long address, final byte value) {
		UNSAFE.putByte(address, value);
	}

	/**
	 * Fetches a value from a given memory address.
	 * 
	 * @param address
	 * @return
	 */
	public static int getInt(final long address) {
		return UNSAFE.getInt(address);
	}

	/**
	 * Stores a value into a given memory address.
	 * 
	 * @param address
	 * @param value
	 */
	public static void putInt(final long address, final int value) {
		UNSAFE.putInt(address, value);
	}

	/**
	 * Get direct buffer address
	 * 
	 * @param buf
	 * @return
	 */
	public static long getBufferAddress(final Buffer buf) {
		return UNSAFE.getLong(buf, bufferAddressOffset);
	}
}