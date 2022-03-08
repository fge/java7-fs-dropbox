/*
 * Copyright (C) 2020 Frédéric Bard
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110, USA
 */

package com.github.fge.fs.dropbox.api;

import java.io.IOException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.dropbox.core.DbxApiException;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DeletedMetadata;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderGetLatestCursorResult;
import com.dropbox.core.v2.files.ListFolderLongpollResult;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;


/**
 * DropBoxWatchService.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/06/30 umjammer initial version <br>
 */
public class DropBoxWatchService implements WatchService {

    private DbxClientV2 dropboxClient;
    private AtomicBoolean hasChanges;
    private boolean continuePolling;

    public DropBoxWatchService(final DbxClientV2 client) {
        dropboxClient = client;
    }

    /**
     * Returns latest cursor for listing changes to a directory in Dropbox with
     * the given path.
     *
     * @param path path to directory in Dropbox
     * @return cursor for listing changes to the given Dropbox directory
     */
    private String getLatestCursor(final String path) throws DbxApiException, DbxException {

        final ListFolderGetLatestCursorResult result = dropboxClient.files()
                .listFolderGetLatestCursorBuilder(path)
                .withIncludeDeleted(true)
                .withIncludeMediaInfo(false)
                .withRecursive(true)
                .start();

        return result.getCursor();
    }

    @Override
    public void close() throws IOException {

        // We need to set this to false and we close the http client below
        // otherwise we will poll again.
        continuePolling = false;
    }

    /**
     * Prints changes made to a folder in Dropbox since the given cursor was
     * retrieved.
     *
     * @param cursor Latest cursor received since last set of changes
     * @return latest cursor after changes
     */
    private String examineChanges(String cursor) throws DbxApiException, DbxException {

        while (true) {
            final ListFolderResult result = dropboxClient.files().listFolderContinue(cursor);
            for (final Metadata metadata : result.getEntries()) {
                if (metadata instanceof FileMetadata || metadata instanceof DeletedMetadata) {
                    hasChanges.getAndSet(true);
                }
            }
            // update cursor to fetch remaining results
            cursor = result.getCursor();

            if (!result.getHasMore()) {
                break;
            }
        }

        return cursor;
    }

    @Override
    public WatchKey poll() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public WatchKey poll(final long timeout, final TimeUnit unit) throws InterruptedException {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * See listFolderLongpoll() description to understand why we can't use the
     * Dropbox SDK function listFolderLongpoll().
     */
    @Override
    public WatchKey take() throws InterruptedException {
        hasChanges = new AtomicBoolean(false);
        continuePolling = true;

        while (hasChanges.get() == false && continuePolling) {
            try {

                final String cursor = getLatestCursor("");

                // final ListFolderLongpollResult listFolderLongpollResult =
                // DropboxClient.getDefault().files().listFolderLongpoll(cursor);
                // if (listFolderLongpollResult.getChanges()) {

                final ListFolderLongpollResult listFolderLongpollResult = DbxClientV2Ex.listFolderLongpoll(cursor);
                if (listFolderLongpollResult == null) {
                    continue;
                }
                if (listFolderLongpollResult.getChanges()) {
                    examineChanges(cursor);
                }

                // we were asked to back off from our polling, wait the
                // requested amount of seconds
                // before issuing another longpoll request.
                final Long backoff = listFolderLongpollResult.getBackoff();
                if (backoff != null) {
                    try {
                        // backing off for %d secs...\n", backoff.longValue());s
                        Thread.sleep(TimeUnit.SECONDS.toMillis(backoff));
                    } catch (final InterruptedException ex) {
                        throw new IOException("Error when backing off from watching the Dropbox folder", ex);
                    }
                }

            } catch (final DbxException | IOException ex) {
            }
        }

        // We return an empty Watchkey. The goal here is that we only
        // want to notify that some changes happened
        final WatchKey dropboxWatchKey = new WatchKey() {

            @Override
            public void cancel() {
                // TODO Auto-generated method stub
            }

            @Override
            public boolean isValid() {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public List<WatchEvent<?>> pollEvents() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public boolean reset() {
                return continuePolling;
            }

            @Override
            public Watchable watchable() {
                // TODO Auto-generated method stub
                return null;
            }
        };

        return dropboxWatchKey;
    }
}
