/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package com.github.fge.fs.dropbox;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;

import vavi.net.auth.oauth2.dropbox.DropBoxLocalAppCredential;
import vavi.net.auth.oauth2.dropbox.DropBoxOAuth2;
import vavi.net.auth.web.dropbox.DropBoxLocalUserCredential;
import vavi.util.Debug;


/**
 * WebHookTest.
 *
 * @depends "file://${HOME}.vavifuse/dropbox/?"
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2016/02/29 umjammer initial version <br>
 */
public class WebHookTest {

    @ClientEndpoint(configurator = AuthorizationConfigurator.class)
    public static class NotificationClient {
        Service service;
        NotificationClient(Service service) {
            this.service = service;
        }
        @OnOpen
        public void onOpen(Session session) throws IOException {
Debug.println("OPEN: " + session);
        }

        @OnMessage
        public void onMessage(String notification) throws IOException {
Debug.println(notification);
            service.process(notification);
        }

        @OnError
        public void onError(Throwable t) {
t.printStackTrace();
        }

        @OnClose
        public void onClose(Session session) throws IOException {
Debug.println("CLOSE");
            service.stop();
        }
    }

    static String username = System.getenv("VAVI_APPS_WEBHOOK_USERNAME");
    static String password = System.getenv("VAVI_APPS_WEBHOOK_PASSWORD");

    public static class AuthorizationConfigurator extends ClientEndpointConfig.Configurator {
        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            headers.put("Authorization", Arrays.asList("Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes())));
        }
    };

    static String websocketBaseUrl = System.getenv("VAVI_APPS_WEBHOOK_WEBSOCKET_BASE_URL");
    static String websocketPath = System.getenv("VAVI_APPS_WEBHOOK_WEBSOCKET_DROPBOX_PATH");
    static String applicationName = System.getenv("APPLICATION_NAME");

    static class Service {
        DbxClientV2 client;
        String savedStartPageToken;
        Session session;

        Service() throws IOException {
            DropBoxLocalUserCredential userCredential = new DropBoxLocalUserCredential(email);
            DropBoxLocalAppCredential appCredential = new DropBoxLocalAppCredential();

            String accessToken = new DropBoxOAuth2(appCredential).authorize(userCredential);

            DbxRequestConfig config = DbxRequestConfig.newBuilder(applicationName).withUserLocaleFrom(Locale.getDefault()).build();
            client = new DbxClientV2(config, accessToken);
        }

        void start() throws IOException {
            try {
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                URI uri = URI.create(websocketBaseUrl + websocketPath);
//                URI uri = URI.create("ws://localhost:5000" + websocketPath);
                session = container.connectToServer(new NotificationClient(this), uri);
//                session.setMaxIdleTimeout(0);
            } catch (DeploymentException e) {
                throw new IOException(e);
            }
        }

        void stop() throws IOException {
            if (session != null) {
                session.close();
                session = null;
            }
        }

        void process(String notification) throws IOException {
Debug.println("notification: " + notification);
        }
    }

    static String email = System.getenv("TEST_ACCOUNT");

    /**
     * @param args 0: email
     */
    public static void main(String[] args) throws Exception {
        WebHookTest app = new WebHookTest();
        app.test();
    }

    void test() throws Exception {
Debug.println("Start");
        Service service = new Service();
        try {
            service.start();

            URI uri = URI.create("dropbox:///?id=" + email);
            FileSystem fs = FileSystems.newFileSystem(uri, Collections.EMPTY_MAP);

System.out.println("ls -l");
Files.list(fs.getPath("/")).forEach(System.out::println);

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

            Thread.sleep(5000);

System.out.println("rm " + remote);
            Files.delete(remote);
        } finally {
            service.stop();
        }
Debug.println("Done");
    }
}
