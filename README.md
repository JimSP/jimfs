Jimfs
=====

Fork jimfs (https://github.com/google/jimfs)
	add custom Map instance for default storage.
	add serializable and comparable interfaces in storage entry
	add unit test for custom Map.

Jimfs is an in-memory file system for Java 7 and above, implementing the
[java.nio.file](http://docs.oracle.com/javase/7/docs/api/java/nio/file/package-summary.html)
abstract file system APIs.

Getting started
---------------

```xml
<dependency>
  <groupId>br.com.cafebinario.jimfs</groupId>
  <artifactId>jimfs</artifactId>
  <version>1.1</version>
</dependency>

<repositories>
...

	<repository>
		<id>mvn-repo</id>
		<url>https://raw.github.com/JimSP/artifacts/master</url>

		<releases>
			<enabled>true</enabled>
		</releases>

		<snapshots>
			<enabled>true</enabled>
		</snapshots>
	</repository>

...
</repositories>



```



Basic use
---------

The simplest way to use Jimfs is to just get a new `FileSystem` instance from the `Jimfs` class and
start using it:

```java
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
...

// For a simple file system with Unix-style paths and behavior:
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
Path foo = fs.getPath("/foo");
Files.createDirectory(foo);

Path hello = foo.resolve("hello.txt"); // /foo/hello.txt
Files.write(hello, ImmutableList.of("hello world"), StandardCharsets.UTF_8);
```

What's supported?
-----------------

Jimfs supports almost all the APIs under `java.nio.file`. It supports:

- Creating, deleting, moving and copying files and directories.
- Reading and writing files with `FileChannel` or `SeekableByteChannel`, `InputStream`,
  `OutputStream`, etc.
- Symbolic links.
- Hard links to regular files.
- `SecureDirectoryStream`, for operations relative to an _open_ directory.
- Glob and regex path filtering with `PathMatcher`.
- Watching for changes to a directory with a `WatchService`.
- File attributes. Built-in attribute views that can be supported include "basic", "owner",
  "posix", "unix", "dos", "acl" and "user". Do note, however, that not all attribute views provide
  _useful_ attributes. For example, while setting and reading POSIX file permissions is possible
  with the "posix" view, those permissions will not actually affect the behavior of the file system.

Jimfs also supports creating file systems that, for example, use Windows-style paths and (to an
extent) behavior. In general, however, file system behavior is modeled after UNIX and may not
exactly match any particular real file system or platform.

License
-------

```
Copyright 2013 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
