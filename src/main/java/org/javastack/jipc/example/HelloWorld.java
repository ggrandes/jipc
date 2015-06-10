package org.javastack.jipc.example;

import java.nio.charset.Charset;

import org.javastack.jipc.Msg;
import org.javastack.jipc.jIPC;

public class HelloWorld {
	private static final Charset Latin1 = Charset.forName("ISO-8859-1");

	public static void main(final String[] args) throws Throwable {
		final jIPC ipc = new jIPC("/tmp/ipc.tmp").open();
		if (ipc.isEmpty()) {
			ipc.put("hello world".getBytes(Latin1));
		} else {
			final Msg msg = ipc.get();
			System.out.println("msg=" + msg);
			System.out.println("data=" + new String(msg.data, Latin1));
		}
		ipc.close();
	}
}
