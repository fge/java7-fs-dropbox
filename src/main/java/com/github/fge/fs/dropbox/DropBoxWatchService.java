/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package com.github.fge.fs.dropbox;

import java.io.IOException;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DeletedMetadata;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderGetLatestCursorResult;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.github.fge.fs.dropbox.webhook.websocket.DropBoxNotification;

import vavi.nio.file.watch.webhook.WebHookBaseWatchService;
import vavi.util.Debug;

import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;


/**
 * DropBoxWatchService.
 * <p>
 * notification source is none (dropbox always send notifications).
 * <p>
 * system properties
 * <ul>
 * <li> vavi.nio.file.watch.webhook.NotificationProvider.dropbox
 * </ul>
 * </p>
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/07/22 umjammer initial version <br>
 * @see "https://github.com/aishraj/dropbox-webhooks-java-example/blob/master/app/controllers/Application.java"
 */
public class DropBoxWatchService extends WebHookBaseWatchService<DropBoxNotification> {

    private static final String WEBHOOK_NOTIFICATION_PROVIDER =
            System.getProperty("vavi.nio.file.watch.webhook.NotificationProvider.dropbox", ".dropbox.webhook.websocket");

    private DbxClientV2 client;

    private String cursor;

    /** */
    public DropBoxWatchService(DbxClientV2 client) throws IOException {
        this.client = client;

        setupNotification(this, WEBHOOK_NOTIFICATION_PROVIDER);

        try {
            ListFolderGetLatestCursorResult result = client.files()
                    .listFolderGetLatestCursorBuilder("")
                    .withIncludeDeleted(true)
                    .withIncludeMediaInfo(false)
                    .withRecursive(true)
                    .start();

            cursor = result.getCursor();
Debug.println("BOX: cursor: " + cursor);
        } catch (DbxException e) {
            throw new IOException(e);
        }
    }

    @Override
    protected void onNotifyMessage(DropBoxNotification notification) throws IOException {
Debug.println(">> notification: " + notification);
        try {
            // TODO limit by account
            boolean hasMore = true;
            while (hasMore) {
                final ListFolderResult result = client.files().listFolderContinue(cursor);
                for (final Metadata metadata : result.getEntries()) {
Debug.println(">> " + metadata.getClass().getName() + ": " + metadata);
                    if (metadata instanceof FileMetadata) {
                        listener.accept(((FileMetadata) metadata).getPathDisplay(), ENTRY_MODIFY);
                    } else if (metadata instanceof DeletedMetadata) {
                        listener.accept(((DeletedMetadata) metadata).getPathDisplay() ,ENTRY_DELETE);
                    }
                }

                cursor = result.getCursor();
                hasMore = result.getHasMore();
            }
        } catch (DbxException e) {
            throw new IOException(e);
        }
Debug.println(">> notification: done");
    }

    @Override
    public void close() throws IOException {
        if (isOpen()) {
            super.close();
        }
    }
}
