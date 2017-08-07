# jIPC

Inter-Process Communication based on SharedMemory (mmaped-file) for Java. Open Source Java project under Apache License v2.0

### Current Stable Version is [1.0.1](https://search.maven.org/#search|ga|1|g%3Aorg.javastack%20a%3Ajipc)

---

## MAVEN

Add the dependency to your pom.xml:

    <dependency>
        <groupId>org.javastack</groupId>
        <artifactId>jipc</artifactId>
        <version>1.0.1</version>
    </dependency>

---

## DOC

#### Usage Example

```java
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
```

* More examples in [Example package](https://github.com/ggrandes/jipc/tree/master/src/main/java/org/javastack/jipc/example/)

### Internal Memory Structure (SharedMem)

#### Header (offset 0):

| magic  | lock   | msgType | msgId   | dataLen |
| :----- | :----- | :------ | :------ | :------ |
| 4bytes | 4bytes | 4bytes  | 4bytes  | 4bytes  |

#### Data (offset 4096):

| data   |
| :----- |
| VarLen |

> **IF:**
> data-len = -1 (no data)

###### Notes:

  - Offsets are relative to 0

---

## Benchmarks

###### Values are not accurate, but orientative. Higher better. All test Running on Laptop { Windows 7 (64bits), Core i5-3337U 1.8Ghz, 8GB Ram }.

| useUnsafe | duplex | jvm    | msg/s   |
| :-------- | :----- | :----- | ------: |
| false     | true   | 32bits |  167000 |
| false     | true   | 64bits |  169000 |
| false     | false  | 32bits |  196000 |
| false     | false  | 64bits |  202000 |
| true      | true   | 32bits |  801000 |
| true      | true   | 64bits | 1109000 |
| true      | false  | 32bits | 1381000 |
| true      | false  | 64bits | 1703000 |

###### Note about [sun.misc.Unsafe](http://java.dzone.com/articles/understanding-sunmiscunsafe)

---
Inspired in System V Shared memory and [clip-library](http://es.slideshare.net/ltsllc/java-ipc-and-the-clip-library), this code is Java-minimalistic version.
