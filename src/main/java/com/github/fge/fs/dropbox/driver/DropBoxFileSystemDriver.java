package com.github.fge.fs.dropbox.driver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxUploader;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.github.fge.filesystem.driver.UnixLikeFileSystemDriverBase;
import com.github.fge.filesystem.exceptions.IsDirectoryException;
import com.github.fge.filesystem.provider.FileSystemFactoryProvider;
import com.github.fge.fs.dropbox.misc.DropBoxIOException;
import com.github.fge.fs.dropbox.misc.DropBoxInputStream;
import com.github.fge.fs.dropbox.misc.DropBoxOutputStream;

@SuppressWarnings("OverloadedVarargsMethod")
@ParametersAreNonnullByDefault
public final class DropBoxFileSystemDriver
    extends UnixLikeFileSystemDriverBase
{
    private final DbxClientV2 client;

    public DropBoxFileSystemDriver(final FileStore fileStore,
        final FileSystemFactoryProvider provider, final DbxClientV2 client)
    {
        super(fileStore, provider);
        this.client = client;
    }

    /** */
    private String toDbxPathString(Path path) throws IOException {
         String pathString = path.toRealPath().toString();
         return pathString.equals("/") ? "" : pathString;
    }

    /** TODO */
    private Map<String, Metadata> cache = new HashMap<String, Metadata>() {{ put("/", new FolderMetadata("/", "/", "/", "0")); }};
    
    /** */
    private Metadata getMetadata(Path path) throws IOException, DbxException {
        String pathString = path.toRealPath().toString();
        if (cache.containsKey(pathString)) {
            return cache.get(pathString);
        } else {
            Metadata metadata = client.files().getMetadata(toDbxPathString(path));
            cache.put(pathString, metadata);
            return metadata;
        }
    }
    
    @Nonnull
    @Override
    public InputStream newInputStream(final Path path,
        final Set<? extends OpenOption> options)
        throws IOException
    {
        try {
            final String target = toDbxPathString(path);

            final Metadata entry = getMetadata(path);
            // TODO: metadata driver
            if (FolderMetadata.class.isInstance(entry))
                throw new IsDirectoryException(target);
    
            final DbxDownloader<?> downloader = client.files().download(target, null);
            return new DropBoxInputStream(downloader);
        } catch (DbxException e) {
            throw new DropBoxIOException("path: " + path, e);
        }
    }

    @Nonnull
    @Override
    public OutputStream newOutputStream(final Path path,
        final Set<? extends OpenOption> options)
        throws IOException
    {
        try {
            final String target = toDbxPathString(path);

            try {
                Metadata entry = getMetadata(path);

                if (FolderMetadata.class.isInstance(entry))
                    throw new IsDirectoryException(target);
            } catch (DbxException e) {
                System.err.println("newOutputStream: " + e.getMessage() + ", path: " + path);
            }
    
            final DbxUploader<?, ?, ?> uploader = client.files().upload(target);
            return new DropBoxOutputStream(uploader); // TODO add cache
        } catch (DbxException e) {
            throw new DropBoxIOException("path: " + path, e);
        }
    }

    // TODO dir cache
    @Nonnull
    @Override
    public DirectoryStream<Path> newDirectoryStream(final Path dir,
        final DirectoryStream.Filter<? super Path> filter)
        throws IOException
    {
        try {
            String target = toDbxPathString(dir);

            final Metadata dirent = getMetadata(dir);
            if (!FolderMetadata.class.isInstance(dirent))
                throw new NotDirectoryException(target);
    
            final List<Metadata> children = client.files().listFolder(target).getEntries();
            final List<Path> list = new ArrayList<>(children.size());
            for (final Metadata child: children) {
                list.add(dir.resolve(child.getName()));
                cache.put(dir.resolve(child.getName()).toRealPath().toString(), child);
            }
    
            //noinspection AnonymousInnerClassWithTooManyMethods
            return new DirectoryStream<Path>()
            {
                private final AtomicBoolean alreadyOpen = new AtomicBoolean(false);
    
                @Override
                public Iterator<Path> iterator()
                {
                    // required by the contract
                    if (alreadyOpen.getAndSet(true))
                        throw new IllegalStateException();
                    return list.iterator();
                }
    
                @Override
                public void close()
                    throws IOException
                {
                }
            };
        } catch (DbxException e) {
            throw new DropBoxIOException("dir: " + dir, e);
        }
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path,
                                              Set<? extends OpenOption> options,
                                              FileAttribute<?>... attrs) throws IOException {
        try {
            if (options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND)) {
                final WritableByteChannel wbc = Channels.newChannel(newOutputStream(path, options));
                long leftover = 0;
                if (options.contains(StandardOpenOption.APPEND)) {
                    Metadata metadata = getMetadata(path);
                    if (metadata != null && FileMetadata.class.cast(metadata).getSize() >= 0)
                        leftover = FileMetadata.class.cast(metadata).getSize();
                }
                final long offset = leftover;
                return new SeekableByteChannel() {
                    long written = offset;

                    public boolean isOpen() {
                        return wbc.isOpen();
                    }

                    public long position() throws IOException {
                        return written;
                    }

                    public SeekableByteChannel position(long pos) throws IOException {
                        throw new UnsupportedOperationException();
                    }

                    public int read(ByteBuffer dst) throws IOException {
                        throw new UnsupportedOperationException();
                    }

                    public SeekableByteChannel truncate(long size) throws IOException {
                        throw new UnsupportedOperationException();
                    }

                    public int write(ByteBuffer src) throws IOException {
                        int n = wbc.write(src);
                        written += n;
                        return n;
                    }

                    public long size() throws IOException {
                        return written;
                    }

                    public void close() throws IOException {
                        wbc.close();
                    }
                };
            } else {
                Metadata metadata = getMetadata(path);
                if (FolderMetadata.class.isInstance(metadata))
                    throw new NoSuchFileException(path.toString());
                final ReadableByteChannel rbc = Channels.newChannel(newInputStream(path, null));
                final long size = FileMetadata.class.cast(metadata).getSize();
                return new SeekableByteChannel() {
                    long read = 0;

                    public boolean isOpen() {
                        return rbc.isOpen();
                    }

                    public long position() throws IOException {
                        return read;
                    }

                    public SeekableByteChannel position(long pos) throws IOException {
                        read = pos;
                        return this;
                    }

                    public int read(ByteBuffer dst) throws IOException {
                        int n = rbc.read(dst);
                        if (n > 0) {
                            read += n;
                        }
                        return n;
                    }

                    public SeekableByteChannel truncate(long size) throws IOException {
                        throw new NonWritableChannelException();
                    }

                    public int write(ByteBuffer src) throws IOException {
                        throw new NonWritableChannelException();
                    }

                    public long size() throws IOException {
                        return size;
                    }

                    public void close() throws IOException {
                        rbc.close();
                    }
                };
            }
        } catch (DbxException e) {
            new DropBoxIOException("path: " + path, e);
        }
        return null;
    }

    @Override
    public void createDirectory(final Path dir, final FileAttribute<?>... attrs)
        throws IOException
    {
        try {
            // TODO: how to diagnose?
            FolderMetadata metadata = client.files().createFolder(toDbxPathString(dir));
            cache.put(toDbxPathString(dir), metadata);
        } catch (DbxException e) {
            throw new DropBoxIOException("dir: " + dir, e);
        }
    }

    @Override
    public void delete(final Path path)
        throws IOException
    {
        try {
            final String target = toDbxPathString(path);

            final Metadata entry = getMetadata(path);
            // TODO: metadata!
            if (FolderMetadata.class.isInstance(entry)) {
                final ListFolderResult list = client.files().listFolder(target);
                if (list.getEntries().size() > 0)
                    throw new DirectoryNotEmptyException(target);
            }

            client.files().delete(target);
            cache.remove(path.toRealPath().toString());
        } catch (DbxException e) {
            throw new DropBoxIOException("path: " + path, e);
        }
    }

    @Override
    public void copy(final Path source, final Path target,
        final Set<CopyOption> options)
        throws IOException
    {
        try {
            final String srcpath = toDbxPathString(source);
            final String dstpath = toDbxPathString(target);
    
            final Metadata dstentry = getMetadata(target);
            if (FolderMetadata.class.isInstance(dstentry)) {
                final ListFolderResult list = client.files().listFolder(dstpath);
                if (list.getEntries().size() > 0)
                    throw new DirectoryNotEmptyException(dstpath);
            }

            // TODO: unknown what happens when a delete operation is performed
            client.files().delete(dstpath);
    
            // TODO: how to diagnose?
            client.files().copy(srcpath, dstpath);
        } catch (DbxException e) {
            throw new DropBoxIOException("source: " + source + ", target: " + target, e);
        }
    }

    @Override
    public void move(final Path source, final Path target,
        final Set<CopyOption> options)
        throws IOException
    {
        try {
            final String srcpath = toDbxPathString(source);
            final String dstpath = toDbxPathString(target);
    
            final Metadata dstentry = getMetadata(target);
            if (FolderMetadata.class.isInstance(dstentry)) {
                final ListFolderResult list = client.files().listFolder(dstpath);
                if (list.getEntries().size() > 0)
                    throw new DirectoryNotEmptyException(dstpath);
            }

            // TODO: unknown what happens when a move operation is performed
            // and the target already exists
            client.files().delete(dstpath);
    
            client.files().move(srcpath, dstpath);
        } catch (DbxException e) {
            throw new DropBoxIOException("source: " + source + ", target: " + target, e);
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
    public void checkAccess(final Path path, final AccessMode... modes)
        throws IOException
    {
        try {
            final String target = toDbxPathString(path);

            final Metadata entry = getMetadata(path);
            if (!FileMetadata.class.isInstance(entry))
                return;
    
            // TODO: assumed; not a file == directory
            for (final AccessMode mode: modes)
                if (mode == AccessMode.EXECUTE)
                    throw new AccessDeniedException(target);
        } catch (DbxException e) {
//System.err.println("checkAccess: " + e.getMessage());
            throw (IOException) new NoSuchFileException("path: " + path).initCause(e);
        }
    }

    @Override
    public void close()
        throws IOException
    {
        // TODO: what to do here? DbxClient does not implement Closeable :(
    }

    /**
     * you should throw FileNotFoundException when an entry for the path is not found.
     * otherwise you will fail mkdir 
     */
    @Nonnull
    @Override
    public Object getPathMetadata(final Path path)
        throws IOException
    {
        try {
            return getMetadata(path);
        } catch (DbxException e) {
            throw (IOException) new NoSuchFileException("path: " + path).initCause(e);
        }
    }
}
