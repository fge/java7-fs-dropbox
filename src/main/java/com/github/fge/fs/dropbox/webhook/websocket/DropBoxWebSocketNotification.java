/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package com.github.fge.fs.dropbox.webhook.websocket;

import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;

import javax.websocket.ClientEndpoint;
import javax.websocket.Session;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import vavi.nio.file.watch.webhook.websocket.BasicAuthorizationConfigurator;
import vavi.nio.file.watch.webhook.websocket.StringWebSocketNotification;
import vavi.util.Debug;


/**
 * DropBoxWebSocketNotification.
 * <p>
 * environment variables
 * <ul>
 * <li> VAVI_APPS_WEBHOOK_WEBSOCKET_BASE_URL
 * <li> VAVI_APPS_WEBHOOK_WEBSOCKET_DROPBOX_PATH
 * </ul>
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/07/22 umjammer initial version <br>
 */
@ClientEndpoint(configurator = BasicAuthorizationConfigurator.class)
public class DropBoxWebSocketNotification extends StringWebSocketNotification {

    private static Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private static final String websocketBaseUrl = System.getenv("VAVI_APPS_WEBHOOK_WEBSOCKET_BASE_URL");
    private static final String websocketPath = System.getenv("VAVI_APPS_WEBHOOK_WEBSOCKET_DROPBOX_PATH");

    private static final URI uri = URI.create(websocketBaseUrl + websocketPath);

    private Consumer<DropBoxNotification> callback;

    /**
     * @param args
     */
    public DropBoxWebSocketNotification(Consumer<DropBoxNotification> callback, Object... args) throws IOException {
        super(uri, args);
        this.callback = callback;
    }

    @Override
    public void onOpenImpl(Session session) throws IOException {
    }

    @Override
    protected void onNotifyMessageImpl(String notification) throws IOException {
Debug.println(">> notification: " + notification);
        DropBoxNotification result = gson.fromJson(notification, DropBoxNotification.class);
        callback.accept(result);
    }

    @Override
    protected void onCloseImpl(Session session) throws IOException {
    }
}

/* */
