/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package com.github.fge.fs.dropbox.webhook.websocket;

import java.util.List;


/**
 * DropBoxNotification.
 * <pre>
 * {"list_folder": {"accounts": ["dbid:AAZZYL-yb_o5hWKxzBr5OHxyzjuZ2k4"]}, "delta": {"users": [362226]}}
 * </pre>
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/07/28 umjammer initial version <br>
 */
public class DropBoxNotification {

    public static class ListFolder {
        List<String> accounts;
        @Override
        public String toString() {
            return "accounts=" + accounts.toString();
        }
    }

    public static class Delta {
        List<String> users;
        @Override
        public String toString() {
            return "users=" + users.toString();
        }
    }

    public ListFolder listFolder;
    public Delta delta;

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("DropBoxNotification [listFolder=");
        builder.append(listFolder);
        builder.append(", delta=");
        builder.append(delta);
        builder.append("]");
        return builder.toString();
    }
}

/* */
