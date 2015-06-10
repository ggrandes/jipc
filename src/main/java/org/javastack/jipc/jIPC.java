package org.javastack.jipc;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.FileLock;
import java.util.Random;


/**
 * Inter-Process Communication based on SharedMemory (mmaped-file) for Java. This is like a <code>Queue</code>
 * of size 1.
 * This class is Thead-Safe and Multi-Process-Safe.
 * 
 * @threadSafe true
 * @see java.util.Queue
 */
public class jIPC {
	/**
	 * Default size for data segment (in bytes)
	 */
	public static final int DEFAULT_SIZE = 4096;
	/**
	 * Type returned when no TYPE
	 */
	public static final int MSG_TYPE_NONE = Integer.MIN_VALUE;
	/**
	 * ID returned when no ID
	 */
	public static final int MSG_ID_NONE = Integer.MIN_VALUE;
	/**
	 * Offset for header: MAGIC
	 */
	private static final int OFF_MAGIC = 0;
	/**
	 * Offset for header: LOCK
	 */
	private static final int OFF_LOCK = 4;
	/**
	 * Offset for header: MSG_TYPE
	 */
	private static final int OFF_MSG_TYPE = 8;
	/**
	 * Offset for header: MSG_ID
	 */
	private static final int OFF_MSG_ID = 12;
	/**
	 * Offset for header: DATA_LEN
	 */
	private static final int OFF_DATA_LEN = 16;
	/**
	 * Offset for data segment
	 */
	private static final int OFF_DATA = 4096;
	/**
	 * Magic for header
	 */
	private static final int MAGIC = 0x0420FFBE;
	/**
	 * Mark for unlocked segment
	 */
	private static final int MARK_UNLOCK = 0;
	/**
	 * Mark for empty segment
	 */
	private static final int MARK_EMPTY = -1;

	/**
	 * Use Unsafe?
	 */
	private static boolean DEFAULT_USE_UNSAFE = false;

	private final File file;
	private final int size;
	private final int markLock;

	private ByteBuffer bb;
	private long bbAddress = 0;
	private FileChannel chan = null;
	private FileLock lock = null;
	private boolean useUnsafe = DEFAULT_USE_UNSAFE;
	private boolean isOpen = false;

	static {
		try {
			DEFAULT_USE_UNSAFE = UnsafeAccess.isUnsafeAvailable();
		} catch (Throwable e) {
			DEFAULT_USE_UNSAFE = false;
		}
	}

	public static boolean getDefaultUseUnsafe() {
		return DEFAULT_USE_UNSAFE;
	}

	/**
	 * Create IPC from specified file with default size
	 * 
	 * @param filename
	 * @throws IOException
	 */
	public jIPC(final String filename) throws IOException {
		this(new File(filename));
	}

	/**
	 * Create IPC from specified file with default size
	 * 
	 * @param file
	 * @throws IOException
	 */
	public jIPC(final File file) throws IOException {
		this(file, DEFAULT_SIZE);
	}

	/**
	 * Create IPC from specified file with custom size
	 * 
	 * @param filename
	 * @param size
	 * @throws IOException
	 */
	public jIPC(final String filename, final int size) throws IOException {
		this(new File(filename), size);
	}

	/**
	 * Create IPC from specified file with custom size
	 * 
	 * @param file
	 * @param size
	 * @throws IOException
	 */
	public jIPC(final File file, final int size) throws IOException {
		this.file = file.getCanonicalFile();
		this.size = OFF_DATA + size;
		this.markLock = generateMarkLock();
	}

	/**
	 * Force (enable/disable) usage of sun.misc.Unsafe. Default AUTO.
	 * 
	 * @param useUnsafe true for speedup
	 * @return
	 */
	public synchronized jIPC useUnsafe(final boolean useUnsafe) {
		if (isOpen) {
			throw new IllegalStateException("Already opened");
		}
		if (UnsafeAccess.isUnsafeAvailable()) {
			this.useUnsafe = useUnsafe;
		}
		return this;
	}

	/**
	 * Get current usage of sun.misc.Unsafe (speedup)
	 * 
	 * @return true if enabled
	 */
	public synchronized boolean useUnsafe() {
		return this.useUnsafe;
	}

	/**
	 * Return the associated file for this IPC
	 * 
	 * @return
	 */
	public File getFile() {
		return file;
	}

	/**
	 * Return the size of IPC in bytes
	 * 
	 * @return
	 */
	public int getSize() {
		return size;
	}

	/**
	 * Open IPC
	 * 
	 * @return
	 * @throws IOException
	 */
	public synchronized jIPC open() throws IOException {
		if (isOpen) {
			throw new IllegalStateException("Already opened");
		}
		RandomAccessFile raf = null;
		FileChannel chan = null;
		try {
			raf = new RandomAccessFile(file, "rw");
			if (raf.length() < size) {
				raf.setLength(size);
			}
			chan = raf.getChannel();
			bb = chan.map(MapMode.READ_WRITE, 0, size);
			bb.order(ByteOrder.nativeOrder());
			if (useUnsafe) {
				bbAddress = UnsafeAccess.getBufferAddress(bb);
			} else {
				this.chan = chan;
			}
			init();
		} catch (IOException e) {
			closeQuietly(chan);
			closeQuietly(raf);
			throw e;
		} finally {
			if (useUnsafe) {
				closeQuietly(chan);
				closeQuietly(raf);
			}
		}
		isOpen = true;
		return this;
	}

	private void closeQuietly(final Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (IOException ign) {
			}
		}
	}

	private int generateMarkLock() {
		final Random r = new Random();
		int n = 0;
		while (n == 0) {
			n = r.nextInt();
		}
		return n;
	}

	private void init() throws IOException {
		try {
			lock();
			if (getInt(OFF_MAGIC) != MAGIC) {
				putInt(OFF_MSG_TYPE, MSG_TYPE_NONE);
				putInt(OFF_MSG_ID, MSG_ID_NONE);
				putInt(OFF_DATA_LEN, MARK_EMPTY);
				putInt(OFF_MAGIC, MAGIC);
			}
		} finally {
			unlock();
		}
	}

	private void lock() throws IOException {
		if (useUnsafe) {
			while (!UnsafeAccess.compareAndSwapInt(null, bbAddress + OFF_LOCK, MARK_UNLOCK, markLock)) {
				Thread.yield();
			}
		} else {
			lock = chan.lock();
		}
	}

	private void unlock() throws IOException {
		if (useUnsafe) {
			while (!UnsafeAccess.compareAndSwapInt(null, bbAddress + OFF_LOCK, markLock, MARK_UNLOCK)) {
				Thread.yield();
			}
		} else {
			lock.release();
		}
	}

	private int getInt(final int off) {
		if (useUnsafe) {
			return UnsafeAccess.getInt(bbAddress + off);
		}
		return bb.getInt(off);
	}

	private void putInt(final int off, final int value) {
		if (useUnsafe) {
			UnsafeAccess.putInt(bbAddress + off, value);
		}
		bb.putInt(off, value);
	}

	private byte getByte(final int off) {
		if (useUnsafe) {
			return UnsafeAccess.getByte(bbAddress + off);
		}
		return bb.get(off);
	}

	private void putByte(final int off, final byte value) {
		if (useUnsafe) {
			UnsafeAccess.putByte(bbAddress + off, value);
		}
		bb.put(off, value);
	}

	/**
	 * Erase (zero) all data in the IPC file
	 * 
	 * @throws IOException
	 */
	public synchronized void clean() throws IOException {
		if (!isOpen) {
			throw new IllegalStateException("Not opened");
		}
		try {
			lock();
			for (int i = OFF_DATA; i < size; i++) {
				putByte(i, (byte) 0);
			}
			putInt(OFF_MSG_TYPE, MSG_TYPE_NONE);
			putInt(OFF_MSG_ID, MSG_ID_NONE);
			putInt(OFF_DATA_LEN, MARK_EMPTY);
			putInt(OFF_MAGIC, MAGIC);
		} finally {
			unlock();
			close();
			file.delete();
		}
	}

	/**
	 * Close this IPC
	 */
	public synchronized void close() {
		closeQuietly(chan);
		bbAddress = 0;
		bb = null;
		isOpen = false;
	}

	/**
	 * Check if IPC is empty
	 * 
	 * @return
	 * @throws IOException
	 */
	public synchronized boolean isEmpty() throws IOException {
		if (!isOpen) {
			throw new IllegalStateException("Not opened");
		}
		try {
			lock();
			return (getInt(OFF_DATA_LEN) == MARK_EMPTY);
		} finally {
			unlock();
		}
	}

	/**
	 * Put data in IPC
	 * 
	 * @param msg
	 * @param blocking
	 * @return true if successful
	 * @throws IOException
	 * 
	 * @see {@link Msg#Msg(int, int, byte[])}
	 */
	public boolean put(final Msg msg, final boolean blocking) throws IOException {
		return put(msg.type, msg.id, msg.data, 0, msg.data.length, blocking);
	}

	/**
	 * Put data in IPC in blocking mode without type or id
	 * 
	 * @param buf
	 * @return true if successful
	 * @throws IOException
	 */
	public boolean put(final byte[] buf) throws IOException {
		return put(MSG_TYPE_NONE, MSG_ID_NONE, buf, 0, buf.length, true);
	}

	/**
	 * Put data in IPC in blocking mode.
	 * 
	 * @param type
	 * @param id
	 * @param buf
	 * @return true if successful
	 * @throws IOException
	 */
	public boolean put(final int type, final int id, final byte[] buf) throws IOException {
		return put(type, id, buf, 0, buf.length, true);
	}

	/**
	 * Put data in IPC
	 * 
	 * @param type
	 * @param id
	 * @param buf
	 * @param off
	 * @param len
	 * @param blocking
	 * @return true if successful
	 * @throws IOException
	 * 
	 * @see {@link Msg#Msg(int, int, byte[])}
	 */
	public synchronized boolean put(final int type, final int id, final byte[] buf, final int off,
			final int len, final boolean blocking) throws IOException {
		if (!isOpen) {
			throw new IllegalStateException("Not opened");
		}
		try {
			if (len > size) {
				throw new BufferOverflowException();
			}
			while (true) {
				lock();
				if (getInt(OFF_DATA_LEN) == MARK_EMPTY) {
					break;
				}
				if (!blocking) {
					return false;
				}
				unlock();
				Thread.yield();
			}
			putInt(OFF_MSG_TYPE, type);
			putInt(OFF_MSG_ID, id);
			putInt(OFF_DATA_LEN, len);
			for (int i = 0; i < len; i++) {
				putByte(OFF_DATA + i, buf[i + off]);
			}
			return true;
		} finally {
			unlock();
		}
	}

	/**
	 * Get data from IPC in blocking mode.
	 * 
	 * @return
	 * @throws IOException
	 */
	public Msg get() throws IOException {
		return get(true);
	}

	/**
	 * Get data from IPC
	 * 
	 * @param blocking
	 * @return null if no data available (in non-blocking mode)
	 * @throws IOException
	 */
	public synchronized Msg get(final boolean blocking) throws IOException {
		if (!isOpen) {
			throw new IllegalStateException("Not opened");
		}
		try {
			int len;
			while (true) {
				lock();
				len = getInt(OFF_DATA_LEN);
				if (len != MARK_EMPTY) {
					break;
				}
				if (!blocking) {
					return null;
				}
				unlock();
				Thread.yield();
			}
			final int type = getInt(OFF_MSG_TYPE);
			final int id = getInt(OFF_MSG_ID);
			final byte[] buf = new byte[len];
			for (int i = 0; i < len; i++) {
				buf[i] = getByte(OFF_DATA + i);
			}
			putInt(OFF_DATA_LEN, MARK_EMPTY);
			return new Msg(type, id, buf);
		} finally {
			unlock();
		}
	}
}
