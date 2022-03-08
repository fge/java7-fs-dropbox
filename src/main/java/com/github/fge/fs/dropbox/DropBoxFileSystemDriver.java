package com.github.fge.fs.dropbox;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.CopyOption;
import java.nio.file.FileStore;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxUploader;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.Metadata;
import com.github.fge.filesystem.driver.CachedFileSystemDriver;
import com.github.fge.filesystem.provider.FileSystemFactoryProvider;

import vavi.nio.file.Util;
import vavi.util.Debug;

import static com.github.fge.fs.dropbox.DropBoxFileSystemProvider.ENV_USE_SYSTEM_WATCHER;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static vavi.nio.file.Util.toPathString;


@ParametersAreNonnullByDefault
public final class DropBoxFileSystemDriver
    extends CachedFileSystemDriver<Metadata> {

    private DbxClientV2 client;

    private DropBoxWatchService systemWatcher;

    public DropBoxFileSystemDriver(
            FileStore fileStore,
            FileSystemFactoryProvider provider,
            DbxClientV2 client,
            Map<String, ?> env) throws IOException {
        super(fileStore, provider);
        this.client = client;
        setEnv(env);

        @SuppressWarnings("unchecked")
        boolean useSystemWatcher = (Boolean) ((Map<String, Object>) env).getOrDefault(ENV_USE_SYSTEM_WATCHER, false);
        if (useSystemWatcher) {
            systemWatcher = new DropBoxWatchService(client);
            systemWatcher.setNotificationListener(this::processNotification);
        }
    }

    /** for system watcher */
    private void processNotification(String pathString, Kind<?> kind) {
        if (ENTRY_DELETE == kind) {
            try {
                Path path = cache.getEntry(e -> pathString.equals(e.getPathDisplay()));
                cache.removeEntry(path);
            } catch (NoSuchElementException e) {
Debug.println("NOTIFICATION: already deleted: " + pathString);
            }
        } else {
            try {
                try {
                    Path path = cache.getEntry(e -> pathString.equals(e.getPathDisplay()));
Debug.println("NOTIFICATION: maybe updated: " + path);
                    cache.removeEntry(path);
                    cache.getEntry(path);
                } catch (NoSuchElementException e) {
// TODO impl
//                    Metadata entry = client.files().getMetadata(pathString);
//                    Path path = parent.resolve(pathString);
//Debug.println("NOTIFICATION: maybe created: " + path);
//                    cache.addEntry(path, entry);
                }
            } catch (NoSuchElementException e) {
Debug.println("NOTIFICATION: parent not found: " + e);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /** */
    private String toDbxPathString(Path path) throws IOException {
         String pathString = toPathString(path);
         return pathString.equals("/") ? "" : pathString;
    }

    @Override
    protected String getFilenameString(Metadata entry) {
        return entry.getName();
    }

    @Override
    protected boolean isFolder(Metadata entry) {
        // ugly
        return FolderMetadata.class.isInstance(entry);
    }

    @Override
    protected Metadata getRootEntry(Path root) throws IOException {
        return new FolderMetadata("/", "0", "/", "/", null, null, null, null);
    }

    @Override
    protected Metadata getEntry(Metadata entry, Path path) throws IOException {
        try {
            return client.files().getMetadata(toDbxPathString(path));
        } catch (GetMetadataErrorException e) {
            return null;
        } catch (DbxException e) {
            throw new IOException("path: " + path, e);
        }
    }

    @Override
    protected InputStream downloadEntry(Metadata entry, Path path, Set<? extends OpenOption> options) throws IOException {
        try {
            final DbxDownloader<?> downloader = client.files().download(toDbxPathString(path), null);
            return new BufferedInputStream(new Util.InputStreamForDownloading(downloader.getInputStream()) {
                @Override
                protected void onClosed() throws IOException {
                    downloader.close();
                }
            });
        } catch (DbxException e) {
            throw new IOException("path: " + path, e);
        }
    }

    @Override
    protected OutputStream uploadEntry(Metadata parentEntry, Path path, Set<? extends OpenOption> options) throws IOException {
        try {
            final DbxUploader<?, ?, ?> uploader = client.files().upload(toDbxPathString(path));
            return new BufferedOutputStream(new Util.OutputStreamForUploading(uploader.getOutputStream()) {
                @Override
                protected void onClosed() throws IOException {
                    try {
                        FileMetadata newEntry = FileMetadata.class.cast(uploader.finish());
                        updateEntry(path, newEntry);
                    } catch (DbxException e) {
                        throw new IOException(e);
                    } finally {
                        uploader.close();
                    }
                }
            });
        } catch (DbxException e) {
            throw new IOException("path: " + path, e);
        }
    }

    @Override
    protected List<Metadata> getDirectoryEntries(Metadata dirEntry, Path dir) throws IOException {
        try {
            return client.files().listFolder(toDbxPathString(dir)).getEntries();
        } catch (DbxException e) {
            throw new IOException("dir: " + dir, e);
        }
    }

    @Override
    protected Metadata createDirectoryEntry(Metadata parentEntry, Path dir) throws IOException {
        try {
            return client.files().createFolderV2(toDbxPathString(dir)).getMetadata();
        } catch (DbxException e) {
            throw new IOException("dir: " + dir, e);
        }
    }

    @Override
    protected boolean hasChildren(Metadata dirEntry, Path dir) throws IOException {
        return getDirectoryEntries(dirEntry, dir).size() > 0;
    }

    /** */
    protected void removeEntry(Metadata entry, Path path) throws IOException {
        try {
            // TODO: unknown what happens when a move operation is performed
            // and the target already exists
            client.files().deleteV2(toDbxPathString(path));
        } catch (DbxException e) {
            throw new IOException("path: " + path, e);
        }
    }

    /** */
    protected Metadata copyEntry(Metadata sourceEntry, Metadata targetParentEntry, Path source, Path target, Set<CopyOption> options) throws IOException {
        try {
            return client.files().copyV2(toDbxPathString(source), toDbxPathString(target)).getMetadata();
        } catch (DbxException e) {
            throw new IOException("path: " + source + ", " + target, e);
        }
    }

    @Override
    protected Metadata moveEntry(Metadata sourceEntry, Metadata targetParentEntry, Path source, Path target, boolean targetIsParent) throws IOException {
        try {
            String targetPathString = toDbxPathString(target);
            return client.files().moveV2(toDbxPathString(source), targetPathString).getMetadata();
        } catch (DbxException e) {
            throw new IOException("path: " + source + ", " + target, e);
        }
    }

    @Override
    protected Metadata moveFolderEntry(Metadata sourceEntry, Metadata targetParentEntry, Path source, Path target, boolean targetIsParent) throws IOException {
        try {
            String targetPathString = toDbxPathString(targetIsParent ? target.resolve(source.getFileName()) : target);
            return client.files().moveV2(toDbxPathString(source), targetPathString).getMetadata();
        } catch (DbxException e) {
            throw new IOException("path: " + source + ", " + target, e);
        }
    }

    @Override
    protected Metadata renameEntry(Metadata sourceEntry, Metadata targetParentEntry, Path source, Path target) throws IOException {
        try {
            return client.files().moveV2(toDbxPathString(source), toDbxPathString(target)).getMetadata();
        } catch (DbxException e) {
            throw new IOException("path: " + source + ", " + target, e);
        }
    }

    @Override
    public WatchService newWatchService() {
        try {
            return new DropBoxWatchService(client);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
