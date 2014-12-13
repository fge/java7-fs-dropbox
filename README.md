## Read me first

This project is licensed under both LGPLv3 and ASL 2.0. See file LICENSE for
more details.

## What this is

This is an implementation of a Java 7
[`FileSystem`](https://docs.oracle.com/javase/7/docs/api/java/nio/file/FileSystem.html) over
[DropBox](https://dropbox.com).

## Version, status

The current version is **0.0.1**. Let that not scare you however, since basically...

It works! And it is available on Maven: `com.github.fge:java7-fs-dropbox:0.0.1`.

There are of course a few limitations:

* Its
[`FileSystemProvider`](https://docs.oracle.com/javase/7/docs/api/java/nio/file/FileSystemProvider.html)
is not yet registered to the VM, so creating a new filesystem requires a bit of code (see code example below).
* No metadata attributes are supported behind the basic ones; attributes cannot be overridden either.
* Opening in append mode is not supported (it is unclear whether the Dropbox API supports this).
* Configuration is ultra simple and minimalistic -- in fact you cannot even open a read-only
  `FileSystem` at this moment.

## Usage

First of all, you MUST have a Dropbox account.  Then you need to [register an
application](https://www.dropbox.com/developers/apps).  This application must have **full access**,
and handle any types of files.

When this is done, you need to generate an OAuth 2 access token (this is also done on the apps page, however
you can also generate one programmatically in theory -- if you know how, tell me!).  The way you store
this access token is entirely dependent upon you, but **do not share it publicly!!**

Then write your code! Here is a short example (imports omitted for brevity):

```java
public final class Main
{
    private static final String ACCESS_TOKEN
        = "yourAccessTokenHere";

    private Main()
    {
        throw new Error("nice try!");
    }

    public static void main(final String... args)
        throws IOException
    {
        /*
         * Create the necessary elements to create a filesystem.
         * Note: the URI _must_ have a scheme of "dropbox", and
         * _must_ be hierarchical.
         */
        final URI uri = URI.create("dropbox://foo/");
        final Map<String, String> env = new HashMap<>();
        env.put("accessToken", ACCESS_TOKEN);

        /*
         * Create the FileSystemProvider; this will be more simple once
         * the filesystem is registered to the JRE, but right now you
         * have to do like that, sorry...
         */
        final FileSystemRepository repository
            = new DropBoxFileSystemRepository();
        final FileSystemProvider provider
            = new DropBoxFileSystemProvider(repository);

        try (
            /*
             * Create the filesystem...
             */
            final FileSystem dropboxfs = provider.newFileSystem(uri, env);
        ) {
            /*
             * And use it! You should of course adapt this code...
             */
            // Equivalent to FileSystems.getDefault().getPath(...)
            final Path src = Paths.get("Example3.java");
            // Here we create a path for our DropBox fs...
            final Path dst = dropboxfs.getPath("/Example3.java");
            // Here we copy the file from our local fs to dropbox!
            Files.copy(src, dst);
        }
    }
}
```
