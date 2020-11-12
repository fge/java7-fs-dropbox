/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package com.github.fge.fs.dropbox;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;

import vavi.net.auth.oauth2.dropbox.DropBoxLocalAppCredential;
import vavi.net.auth.oauth2.dropbox.DropBoxOAuth2;
import vavi.net.auth.web.dropbox.DropBoxLocalUserCredential;
import vavi.util.Debug;


/**
 * WebHookTest3. google drive, using now construction libraries.
 *
 * @depends "file://${HOME}.vavifuse/googledrive/?"
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2016/02/29 umjammer initial version <br>
 */
public class WebHookTest3 {

    static String email = System.getenv("TEST_ACCOUNT");
    static String applicationName = System.getenv("APPLICATION_NAME");

    /**
     * @param args 0: email
     *
     * https://developers.google.com/drive/api/v3/reference/changes/watch
     * https://stackoverflow.com/a/43793313/6102938
     *  TODO needs domain authorize
     */
    public static void main(String[] args) throws Exception {
        WebHookTest3 app = new WebHookTest3();
        app.test();
    }

    void test() throws Exception {
        DropBoxLocalUserCredential userCredential = new DropBoxLocalUserCredential(email);
        DropBoxLocalAppCredential appCredential = new DropBoxLocalAppCredential();

        String accessToken = new DropBoxOAuth2(appCredential).authorize(userCredential);

        DbxRequestConfig config = DbxRequestConfig.newBuilder(applicationName).withUserLocaleFrom(Locale.getDefault()).build();
        DbxClientV2 client = new DbxClientV2(config, accessToken);

        DropBoxWatchService service = new DropBoxWatchService(client);
Debug.println("WEBSOCKET: start: " + service);
        try {
            URI uri = URI.create("dropbox:///?id=" + email);
            FileSystem fs = FileSystems.newFileSystem(uri, Collections.EMPTY_MAP);

            Path tmpDir = fs.getPath("tmp");
            if (!Files.exists(tmpDir)) {
System.out.println("rmdir " + tmpDir);
                Files.createDirectory(tmpDir);
            }
            Path remote = tmpDir.resolve("Test+Watch");
            if (Files.exists(remote)) {
System.out.println("rm " + remote);
                Files.delete(remote);
            }
            Path source = Paths.get("src/test/resources", "Hello.java");
System.out.println("cp " + source + " " + remote);
            Files.copy(source, remote);

System.out.println("rm " + remote);
            Files.delete(remote);

            Thread.sleep(10000);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            service.close();
        }
Debug.println("APP: done");
        DropBoxWatchService.dispose();
//Thread.getAllStackTraces().keySet().forEach(System.err::println);
    }
}
