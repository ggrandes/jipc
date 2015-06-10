package org.javastack.jipc;

import java.util.Arrays;

public class Msg {
	/**
	 * Meta Info: Type of Message
	 */
	public final int type;
	/**
	 * Meta Info: Message Identifier
	 */
	public final int id;
	/**
	 * Raw data for message
	 */
	public final byte[] data;

	/**
	 * Create a IPC Message
	 * 
	 * @param type is Type of Message
	 * @param id is a Message Identifier
	 * @param data are raw bytes for message
	 */
	public Msg(final int type, final int id, final byte[] data) {
		this.type = type;
		this.id = id;
		this.data = data;
	}

	@Override
	public boolean equals(final Object other) {
		if (other instanceof Msg) {
			final Msg o = (Msg) other;
			if ((this.type == o.type) && (this.id == o.id)) {
				return Arrays.equals(this.data, o.data);
			}
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (type ^ id ^ Arrays.hashCode(data));
	}

	@Override
	public String toString() {
		return getClass().getName() + "@" + Integer.toHexString(hashCode()) + //
				"[type=" + (type == jIPC.MSG_TYPE_NONE ? "NONE" : String.valueOf(type)) + //
				" id=" + (id == jIPC.MSG_ID_NONE ? "NONE" : String.valueOf(id)) + "]";
	}
}
