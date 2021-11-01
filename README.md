[![Release](https://jitpack.io/v/umjammer/java7-fs-dropbox.svg)](https://jitpack.io/#umjammer/java7-fs-dropbox) [![Actions Status](https://github.com/umjammer/java7-fs-dropbox/workflows/Java%20CI/badge.svg)](https://github.com/umjammer/java7-fs-dropbox/actions) [![Parent](https://img.shields.io/badge/Parent-vavi--apps--fuse-pink)](https://github.com/umjammer/vavi-apps-fuse)

## java7-fs-dropbox

This project is licensed under both LGPLv3 and ASL 2.0. See file LICENSE for
more details.

## What this is

This is an implementation of a Java 7
[`FileSystem`](https://docs.oracle.com/javase/7/docs/api/java/nio/file/FileSystem.html) over
[DropBox](https://dropbox.com).

## Install

### jars

 * https://jitpack.io/#umjammer/java7-fs-dropbox

### selenium chrome driver

 * Download the [chromedriver executable](https://chromedriver.chromium.org/downloads) and locate it into some directory.
   * Don't forget to run jvm with jvm argument `-Dwebdriver.chrome.driver=/usr/local/bin/chromedriver`.

## Usage

First of all, you MUST have a Dropbox account. Then you need to [register an
application](https://www.dropbox.com/developers/apps). This application must have **full access**,
and handle any types of files.

Next, prepare 2 property files.

 * application credential

```shell
$ cat ${HOME}/.vavifuse/dropbox.properties
dropbox.applicationName=your_application_name
dropbox.clientId=your_client_id
dropbox.clientSecret=your_client_secret
dropbox.redirectUrl=http://localhost:30000
dropbox.scopes=files.content.write
```

 * user credential

```shell
$ cat ${HOME}/.vavifuse/credentials.properties
dropbox.password.xxx@yyy.zzz=your_password
```

Then write your code! Here is a short example (imports omitted for brevity):

```java
public class Main {

    public static void main(String[] args) throws IOException {
        String email = "xxx@yyy.zzz";

        URI uri = URI.create("dropbox:///?id=" + email);

        FileSystem fs = FileSystems.newFileSystem(uri, env);
            :
    }
}
```

### See also

https://github.com/umjammer/vavi-apps-fuse/blob/master/vavi-nio-file-gathered/src/test/java/vavi/nio/file/dropbox/Main.java