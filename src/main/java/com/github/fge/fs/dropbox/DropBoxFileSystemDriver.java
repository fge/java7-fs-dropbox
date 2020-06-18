package com.github.fge.fs.dropbox;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxUploader;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.Metadata;
import com.github.fge.filesystem.driver.ExtendedFileSystemDriverBase;
import com.github.fge.filesystem.exceptions.IsDirectoryException;
import com.github.fge.filesystem.provider.FileSystemFactoryProvider;

import vavi.nio.file.Cache;
import vavi.nio.file.Util;
import vavi.util.Debug;

import static vavi.nio.file.Util.toPathString;

@ParametersAreNonnullByDefault
public final class DropBoxFileSystemDriver
    extends ExtendedFileSystemDriverBase
{
    private final DbxClientV2 client;
    private boolean ignoreAppleDouble = false;

    @SuppressWarnings("unchecked")
    public DropBoxFileSystemDriver(final FileStore fileStore,
        final FileSystemFactoryProvider provider, final DbxClientV2 client, final Map<String, ?> env)
    {
        super(fileStore, provider);
        this.client = client;
        ignoreAppleDouble = (Boolean) ((Map<String, Object>) env).getOrDefault("ignoreAppleDouble", Boolean.FALSE);
    }

    /** */
    private String toDbxPathString(Path path) throws IOException {
         String pathString = toPathString(path);
         return pathString.equals("/") ? "" : pathString;
    }

    /** ugly */
    private boolean isFile(Metadata entry) {
        return FileMetadata.class.isInstance(entry);
    }

    /** ugly */
    private boolean isFolder(Metadata entry) {
        return FolderMetadata.class.isInstance(entry);
    }

    /** */
    private Cache<Metadata> cache = new Cache<Metadata>() {
        /**
         * @see #ignoreAppleDouble
         * @throws NoSuchFileException must be thrown when the path is not found in this cache
         */
        public Metadata getEntry(Path path) throws IOException {
            if (cache.containsFile(path)) {
                return cache.getFile(path);
            } else {
                if (ignoreAppleDouble && path.getFileName() != null && Util.isAppleDouble(path)) {
                    throw new NoSuchFileException("ignore apple double file: " + path);
                }

                try {
                    Metadata entry;
                    if (path.getNameCount() == 0) {
                        entry = new FolderMetadata("/", "0", "/", "/", null, null, null, null);
                    } else {
                        entry = client.files().getMetadata(toDbxPathString(path));
                    }
                    cache.putFile(path, entry);
                    return entry;
                } catch (GetMetadataErrorException e) {
                    cache.removeEntry(path);
                    throw new NoSuchFileException(path.toString());
                } catch (DbxException e) {
                    throw new IOException("path: " + path, e);
                }
            }
        }
    };

    @Nonnull
    @Override
    public InputStream newInputStream(final Path path,
        final Set<? extends OpenOption> options)
        throws IOException
    {
        try {
            final Metadata entry = cache.getEntry(path);

            if (isFolder(entry)) {
                throw new IsDirectoryException(path.toString());
            }

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

    @Nonnull
    @Override
    public OutputStream newOutputStream(final Path path,
        final Set<? extends OpenOption> options)
        throws IOException
    {
        try {
            try {
                Metadata entry = cache.getEntry(path);

                if (isFolder(entry)) {
                    throw new IsDirectoryException(path.toString());
                } else {
                    throw new FileAlreadyExistsException(path.toString());
                }
            } catch (NoSuchFileException e) {
Debug.println("newOutputStream: " + e.getMessage());
            }

            final DbxUploader<?, ?, ?> uploader = client.files().upload(toDbxPathString(path));
            return new BufferedOutputStream(new Util.OutputStreamForUploading(uploader.getOutputStream()) {
                @Override
                protected void onClosed() throws IOException {
                    try {
                        FileMetadata newEntry = FileMetadata.class.cast(uploader.finish());
                        cache.addEntry(path, newEntry);
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

    @Nonnull
    @Override
    public DirectoryStream<Path> newDirectoryStream(final Path dir,
        final DirectoryStream.Filter<? super Path> filter)
        throws IOException
    {
        try {
            return Util.newDirectoryStream(getDirectoryEntries(dir), filter);
        } catch (DbxException e) {
            throw new IOException("dir: " + dir, e);
        }
    }

    @Override
    public void createDirectory(final Path dir, final FileAttribute<?>... attrs)
        throws IOException
    {
        try {
            // TODO: how to diagnose?
            FolderMetadata metadata = client.files().createFolderV2(toDbxPathString(dir)).getMetadata();
            cache.addEntry(dir, metadata);
        } catch (DbxException e) {
            throw new IOException("dir: " + dir, e);
        }
    }

    @Override
    public void delete(final Path path)
        throws IOException
    {
        try {
            removeEntry(path);
        } catch (DbxException e) {
            throw new IOException("path: " + path, e);
        }
    }

    @Override
    public void copy(final Path source, final Path target,
        final Set<CopyOption> options)
        throws IOException
    {
        try {
            if (cache.existsEntry(target)) {
                if (options != null && options.stream().anyMatch(o -> o.equals(StandardCopyOption.REPLACE_EXISTING))) {
                    removeEntry(target);
                } else {
                    throw new FileAlreadyExistsException(target.toString());
                }
            }
            copyEntry(source, target);
        } catch (DbxException e) {
            throw new IOException("source: " + source + ", target: " + target, e);
        }
    }

    @Override
    public void move(final Path source, final Path target,
        final Set<CopyOption> options)
        throws IOException
    {
        try {
            if (cache.existsEntry(target)) {
                if (isFolder(cache.getEntry(target))) {
                    if (options != null && options.stream().anyMatch(o -> o.equals(StandardCopyOption.REPLACE_EXISTING))) {
                        // replace the target
                        if (cache.getChildCount(target) > 0) {
                            throw new DirectoryNotEmptyException(target.toString());
                        } else {
                            removeEntry(target);
                            moveEntry(source, target, false);
                        }
                    } else {
                        // move into the target
                        // TODO SPEC is FileAlreadyExistsException ?
                        moveEntry(source, target, true);
                    }
                } else {
                    if (options != null && options.stream().anyMatch(o -> o.equals(StandardCopyOption.REPLACE_EXISTING))) {
                        removeEntry(target);
                        moveEntry(source, target, false);
                    } else {
                        throw new FileAlreadyExistsException(target.toString());
                    }
                }
            } else {
                if (source.getParent().equals(target.getParent())) {
                    // rename
                    renameEntry(source, target);
                } else {
                    moveEntry(source, target, false);
                }
            }
        } catch (DbxException e) {
            throw new IOException("source: " + source + ", target: " + target, e);
        }
    }

    /**
     * Check access modes for a path on this filesystem
     * <p>If no modes are provided to check for, this simply checks for the
     * existence of the path.</p>
     *
     * @param path the path to check
     * @param modes the modes to check for, if any
     * @throws IOException filesystem level error, or a plain I/O error
     * @see FileSystemProvider#checkAccess(Path, AccessMode...)
     */
    @Override
    protected void checkAccessImpl(final Path path, final AccessMode... modes)
        throws IOException
    {
        final Metadata entry = cache.getEntry(path);
        if (!isFile(entry))
            return;

        // TODO: assumed; not a file == directory
        for (final AccessMode mode: modes)
            if (mode == AccessMode.EXECUTE)
                throw new AccessDeniedException(path.toString());
    }

    @Override
    public void close()
        throws IOException
    {
        // TODO: what to do here? DbxClient does not implement Closeable :(
    }

    /**
     * @throws IOException you should throw FileNotFoundException when an entry for the path is not found.
     * otherwise you will fail mkdir 
     */
    @Nonnull
    @Override
    protected Object getPathMetadataImpl(final Path path)
        throws IOException
    {
        return cache.getEntry(path);
    }

    /** */
    private List<Path> getDirectoryEntries(Path dir) throws IOException, DbxException {
        final Metadata entry = cache.getEntry(dir);

        if (!isFolder(entry)) {
//System.err.println(entry.name + ", " + entry.id + ", " + entry.hashCode());
            throw new NotDirectoryException(dir.toString());
        }

        List<Path> list = null;
        if (cache.containsFolder(dir)) {
            list = cache.getFolder(dir);
        } else {
            final List<Metadata> children = client.files().listFolder(toDbxPathString(dir)).getEntries();
            list = new ArrayList<>(children.size());

            for (final Metadata child: children) {
                Path childPath = dir.resolve(child.getName());
                list.add(childPath);
                cache.putFile(childPath, child);
            }
            cache.putFolder(dir, list);
        }

        return list;
    }

    /** */
    private void removeEntry(Path path) throws IOException, DbxException {
        Metadata entry = cache.getEntry(path);
        if (isFolder(entry)) {
            if (getDirectoryEntries(path).size() > 0) {
                throw new DirectoryNotEmptyException(path.toString());
            }
        }

        // TODO: unknown what happens when a move operation is performed
        // and the target already exists
        client.files().deleteV2(toDbxPathString(path));

        cache.removeEntry(path);
    }

    /** */
    private void copyEntry(final Path source, final Path target) throws IOException, DbxException {
        Metadata sourceEntry = cache.getEntry(source);
        if (isFile(sourceEntry)) {
            Metadata newEntry = client.files().copyV2(toDbxPathString(source), toDbxPathString(target)).getMetadata();
            cache.addEntry(target, newEntry);
        } else if (isFolder(sourceEntry)) {
            // TODO java spec. allows empty folder
            throw new IsDirectoryException("source can not be a folder: " + source);
        }
    }

    /**
     * @param targetIsParent if the target is folder
     */
    private void moveEntry(final Path source, final Path target, boolean targetIsParent) throws IOException, DbxException {
        Metadata sourceEntry = cache.getEntry(source);
        if (isFile(sourceEntry)) {
            String targetPathString = toDbxPathString(targetIsParent ? target.resolve(source.getFileName()) : target);
            Metadata patchedEntry = client.files().moveV2(toDbxPathString(source), targetPathString).getMetadata();
            cache.removeEntry(source);
            if (targetIsParent) {
                cache.addEntry(target.resolve(source.getFileName()), patchedEntry);
            } else {
                cache.addEntry(target, patchedEntry);
            }
        } else if (isFolder(sourceEntry)) {
            String targetPathString = toDbxPathString(target);
            Metadata patchedEntry = client.files().moveV2(toDbxPathString(source), targetPathString).getMetadata();
            cache.moveEntry(source, target, patchedEntry);
        }
    }

    /** */
    private void renameEntry(final Path source, final Path target) throws IOException, DbxException {
        Metadata patchedEntry = client.files().moveV2(toDbxPathString(source), toDbxPathString(target)).getMetadata();
        cache.removeEntry(source);
        cache.addEntry(target, patchedEntry);
    }
}
