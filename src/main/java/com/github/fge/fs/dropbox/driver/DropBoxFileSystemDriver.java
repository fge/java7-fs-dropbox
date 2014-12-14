package com.github.fge.fs.dropbox.driver;

import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxWriteMode;
import com.github.fge.filesystem.attributes.FileAttributesFactory;
import com.github.fge.filesystem.driver.UnixLikeFileSystemDriverBase;
import com.github.fge.filesystem.exceptions.IsDirectoryException;
import com.github.fge.fs.dropbox.misc.DropBoxIOException;
import com.github.fge.fs.dropbox.misc.DropBoxInputStream;
import com.github.fge.fs.dropbox.misc.DropBoxOutputStream;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
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
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("OverloadedVarargsMethod")
@ParametersAreNonnullByDefault
public final class DropBoxFileSystemDriver
    extends UnixLikeFileSystemDriverBase
{
    private final DbxClient client;

    public DropBoxFileSystemDriver(final URI uri, final FileStore fileStore,
        final FileAttributesFactory factory, final DbxClient client)
    {
        super(uri, fileStore, factory);
        this.client = client;
    }

    @Nonnull
    @Override
    public InputStream newInputStream(final Path path,
        final OpenOption... options)
        throws IOException
    {
        // TODO: need a "shortcut" way for that; it's quite common
        final String target = path.toRealPath().toString();
        final DbxEntry entry;

        try {
            entry = client.getMetadata(target);
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
        }

        if (entry == null)
            throw new NoSuchFileException(target);
        if (entry.isFolder())
            throw new IsDirectoryException(target);

        final DbxClient.Downloader downloader;

        try {
            downloader = client.startGetFile(target, null);
        } catch (DbxException e) {
            throw new DropBoxIOException(e);
        }

        return new DropBoxInputStream(downloader);
    }

    @Nonnull
    @Override
    public OutputStream newOutputStream(final Path path,
        final OpenOption... options)
        throws IOException
    {
        final Set<OpenOption> opts = arrayToSet(options);

        // TODO: the API does not seem to support append
        if (opts.contains(StandardOpenOption.APPEND))
            throw new UnsupportedOperationException("append not supported");

        // TODO: need a "shortcut" way for that; it's quite common
        final String target = path.toRealPath().toString();
        final DbxEntry entry;


        try {
            entry = client.getMetadata(target);
        } catch (DbxException e) {
            throw new DropBoxIOException(e);
        }

        if (entry != null) {
            if (opts.contains(StandardOpenOption.CREATE_NEW))
                throw new FileAlreadyExistsException(target);
            if (entry.isFolder())
                throw new IsDirectoryException(target);
        }

        final DbxClient.Uploader uploader
            = client.startUploadFileChunked(target, DbxWriteMode.force(), -1L);

        return new DropBoxOutputStream(uploader);
    }

    @Nonnull
    @Override
    public DirectoryStream<Path> newDirectoryStream(final Path dir,
        final DirectoryStream.Filter<? super Path> filter)
        throws IOException
    {
        // TODO: need a "shortcut" way for that; it's quite common
        final String target = dir.toRealPath().toString();
        final DbxEntry.WithChildren dirent;
        try {
            dirent = client.getMetadataWithChildren(target);
        } catch (DbxException e) {
            throw new DropBoxIOException(e);
        }

        if (dirent == null)
            throw new NoSuchFileException(target);
        if (!dirent.entry.isFolder())
            throw new NotDirectoryException(target);

        final List<DbxEntry> children = dirent.children;
        final List<Path> list = new ArrayList<>(children.size());

        for (final DbxEntry child: children)
            list.add(dir.resolve(child.name));

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
    }

    @Override
    public void createDirectory(final Path dir, final FileAttribute<?>... attrs)
        throws IOException
    {
        // TODO: true only for now?
        if (attrs.length != 0)
            throw new UnsupportedOperationException();

        // TODO: need a "shortcut" way for that; it's quite common
        final String target = dir.toRealPath().toString();
        try {
            if (client.getMetadata(target) != null)
                throw new FileAlreadyExistsException(target);
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
        }

        try {
            // TODO: how to diagnose?
            if (client.createFolder(target) == null)
                throw new DropBoxIOException("cannot create directory??");
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
        }
    }

    @Override
    public void delete(final Path path)
        throws IOException
    {
        // TODO: need a "shortcut" way for that; it's quite common
        final String target = path.toRealPath().toString();

        final DbxEntry.WithChildren entry;

        try {
            entry = client.getMetadataWithChildren(target);
            if (entry == null)
                throw new NoSuchFileException(target);
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
        }

        if (entry.entry.isFolder() && !entry.children.isEmpty())
            throw new DirectoryNotEmptyException(target);

        try {
            client.delete(target);
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
        }
    }

    @Override
    public void copy(final Path source, final Path target,
        final CopyOption... options)
        throws IOException
    {
        final Set<CopyOption> opts = arrayToSet(options);
        // TODO: for now only?
        if (opts.contains(StandardCopyOption.COPY_ATTRIBUTES))
            throw new UnsupportedOperationException();

        final String srcpath = source.toRealPath().toString();
        final String dstpath = source.toRealPath().toString();

        final DbxEntry.WithChildren srcentry, dstentry;

        try {
            srcentry = client.getMetadataWithChildren(srcpath);
            dstentry = client.getMetadataWithChildren(dstpath);
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
        }

        if (srcentry == null)
            throw new NoSuchFileException(srcpath);

        final boolean replace
            = opts.contains(StandardCopyOption.REPLACE_EXISTING);

        if (dstentry != null) {
        	if (!replace)
        		throw new FileAlreadyExistsException(dstpath);

            if (dstentry.entry.isFolder() && !dstentry.children.isEmpty())
                throw new DirectoryNotEmptyException(dstpath);
            // TODO: unknown what happens when a copy operation is performed
            // and the target already exists
            try {
                client.delete(dstpath);
            } catch (DbxException e) {
                throw DropBoxIOException.wrap(e);
            }
        }

        try {
            // TODO: how to diagnose?
            if (client.copy(srcpath, dstpath) == null)
                throw new DropBoxIOException("cannot copy??");
        } catch (DbxException e) {
            throw new DropBoxIOException(e);
        }
    }

    @Override
    public void move(final Path source, final Path target,
        final CopyOption... options)
        throws IOException
    {
        final Set<CopyOption> opts = arrayToSet(options);
        // TODO: for now only?
        if (opts.contains(StandardCopyOption.COPY_ATTRIBUTES))
            throw new UnsupportedOperationException();

        final String srcpath = source.toRealPath().toString();
        final String dstpath = source.toRealPath().toString();

        final DbxEntry.WithChildren srcentry, dstentry;

        try {
            srcentry = client.getMetadataWithChildren(srcpath);
            dstentry = client.getMetadataWithChildren(dstpath);
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
        }

        if (srcentry == null)
            throw new NoSuchFileException(srcpath);

        final boolean replace
            = opts.contains(StandardCopyOption.REPLACE_EXISTING);

        if (dstentry != null) {
        	if (!replace)
        		throw new FileAlreadyExistsException(dstpath);

            if (dstentry.entry.isFolder() && !dstentry.children.isEmpty())
                throw new DirectoryNotEmptyException(dstpath);
            // TODO: unknown what happens when a move operation is performed
            // and the target already exists
            try {
                client.delete(dstpath);
            } catch (DbxException e) {
                throw DropBoxIOException.wrap(e);
            }
        }

        try {
            client.move(srcpath, dstpath);
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
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
        final String target = path.toRealPath().toString();

        final DbxEntry entry;

        try {
            entry = client.getMetadata(target);
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
        }

        if (entry == null)
            throw new NoSuchFileException(target);

        final Set<AccessMode> set = arrayToSet(modes);

        // All is legal except EXECUTE on files
        if (entry.isFile() && set.contains(AccessMode.EXECUTE))
            throw new AccessDeniedException(target);
    }

    @Override
    public void close()
        throws IOException
    {
        // TODO: what to do here? DbxClient does not implement Closeable :(
    }

    @Nonnull
    @Override
    public Object getPathMetadata(final Path path)
        throws IOException
    {
        try {
            return client.getMetadata(path.toRealPath().toString());
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
        }
    }

    // TODO: make FileSystemProviderBase do that
    @SafeVarargs
    private static <T> Set<T> arrayToSet(final T... args)
    {
        if (args.length == 0)
            return Collections.emptySet();
        final Set<T> set = new HashSet<>();
        for (final T arg: args)
            set.add(Objects.requireNonNull(arg));
        return Collections.unmodifiableSet(set);
    }
}
