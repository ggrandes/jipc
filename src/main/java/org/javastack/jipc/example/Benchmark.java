package org.javastack.jipc.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.javastack.jipc.Msg;
import org.javastack.jipc.jIPC;

public class Benchmark {
	public static void main(String[] args) throws Throwable {
		final int TOTAL = (int) 1e6;
		final boolean duplex = true;
		final boolean forceUseUnsafe = jIPC.getDefaultUseUnsafe();
		final String dir = System.getProperty("java.io.tmpdir", "/tmp/");
		final Boolean master = !Boolean.getBoolean("benchmark.slave");
		final String qwrite = System.getProperty("benchmark.write");
		final String qread = System.getProperty("benchmark.read");
		final String q1 = "AtoB", q2 = "BtoA";
		if (master) {
			final long start = System.currentTimeMillis() + 2000;
			System.out.println("TOTAL=" + TOTAL);
			Process p1 = Runtime.getRuntime().exec(new String[] {
					"java", "-cp", System.getProperty("java.class.path"), //
					"-Dbenchmark.write=" + q1, "-Dbenchmark.read=" + q2, //
					"-Dbenchmark.slave=true", "-Dbenchmark.start=" + start, //
					Benchmark.class.getName()
			});
			Process p2 = Runtime.getRuntime().exec(new String[] {
					"java", "-cp", System.getProperty("java.class.path"), //
					"-Dbenchmark.write=" + q2, "-Dbenchmark.read=" + q1, //
					"-Dbenchmark.slave=true", "-Dbenchmark.start=" + start, //
					Benchmark.class.getName()
			});
			BufferedReader in1 = new BufferedReader(new InputStreamReader(p1.getInputStream()));
			BufferedReader in2 = new BufferedReader(new InputStreamReader(p2.getInputStream()));
			String line = null;
			while (true) {
				line = in1.readLine();
				if ((line == null) || ("end".equals(line)))
					break;
				System.out.println("in1=" + line);
			}
			while (true) {
				line = in2.readLine();
				if ((line == null) || ("end".equals(line)))
					break;
				System.out.println("in2=" + line);
			}
			p1.waitFor();
			p2.waitFor();
			System.out.println("cleaning IPC");
			new jIPC(new File(dir, "mmap." + q1 + ".tmp")).open().clean();
			new jIPC(new File(dir, "mmap." + q2 + ".tmp")).open().clean();
			System.out.println("ending master");
			return;
		}
		final long start = Long.getLong("benchmark.start");
		final Charset latin1 = Charset.forName("ISO-8859-1");
		// Write
		final Thread tw = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final jIPC ipc = new jIPC(new File(dir, "mmap." + qwrite + ".tmp")) //
							.useUnsafe(forceUseUnsafe).open();
					while (System.currentTimeMillis() < start) {
						Thread.sleep(1);
					}
					final long nanos = System.nanoTime();
					for (int i = 0; i < TOTAL; i++) {
						ipc.put(String.valueOf(i).getBytes(latin1));
					}
					ipc.put(String.valueOf("quit").getBytes(latin1));
					final long diff = (System.nanoTime() - nanos);
					System.out.println(qwrite + //
							" total=" + TOTAL + //
							" millis=" + (diff / 1000000) + "ms" + //
							" micros=" + (diff / 1000) + "us" + //
							" nanos=" + diff + "ns" + //
							" speed(r/ms)=" + (TOTAL / (diff / 1000000)));
					ipc.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		// Read
		final Thread tr = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final jIPC ipc = new jIPC(new File(dir, "mmap." + qread + ".tmp")) //
							.useUnsafe(forceUseUnsafe).open();
					String cmd = null;
					while (System.currentTimeMillis() < start) {
						Thread.sleep(1);
					}
					final long nanos = System.nanoTime();
					while (!"quit".equals(cmd)) {
						final Msg buf = ipc.get();
						cmd = new String(buf.data, latin1);
					}
					final long diff = (System.nanoTime() - nanos);
					System.out.println(qread + //
							" total=" + TOTAL + //
							" millis=" + (diff / 1000000) + "ms" + //
							" micros=" + (diff / 1000) + "us" + //
							" nanos=" + diff + "ns" + //
							" speed(r/ms)=" + (TOTAL / (diff / 1000000)));
					ipc.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		//
		if (duplex) {
			tw.start();
			tr.start();
			tw.join();
			tr.join();
		} else {
			if (q1.equals(qwrite)) {
				tw.start();
				tw.join();
			} else {
				tr.start();
				tr.join();
			}
		}
		System.out.println("end");
	}
}
