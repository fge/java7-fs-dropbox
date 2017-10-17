package com.github.fge.fs.dropbox.driver;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.CreateFolderErrorException;
import com.dropbox.core.v2.files.CreateFolderResult;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.RelocationResult;
import com.dropbox.core.v2.files.UploadUploader;
import com.dropbox.core.v2.files.WriteMode;
import com.github.fge.filesystem.driver.UnixLikeFileSystemDriverBase;
import com.github.fge.filesystem.exceptions.IsDirectoryException;
import com.github.fge.filesystem.provider.FileSystemFactoryProvider;
import com.github.fge.fs.dropbox.misc.DropBoxIOException;
import com.github.fge.fs.dropbox.misc.DropBoxInputStream;
import com.github.fge.fs.dropbox.misc.DropBoxOutputStream;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Nonnull
    @Override
    public InputStream newInputStream(final Path path,
        final Set<OpenOption> options)
        throws IOException
    {
        final String target = path.toRealPath().toString();
        final Metadata metadata = getMetadata(target);

        // TODO: metadata driver
        if (metadata instanceof FolderMetadata)
            throw new IsDirectoryException(target);

        final DbxDownloader<FileMetadata> downloader;

        try {
            downloader = client.files().download(target);
        } catch (DbxException e) {
            throw new DropBoxIOException(e);
        }

        return new DropBoxInputStream(downloader);
    }

    @Nonnull
    @Override
    public OutputStream newOutputStream(final Path path,
        final Set<OpenOption> options)
        throws IOException
    {
        final String target = path.toRealPath().toString();
        Metadata metadata = null;
        try {
            metadata = getMetadata(target);
        } catch (NoSuchFileException e) {
            //ignore
        }

        // TODO: metadata
        if (metadata instanceof FolderMetadata)
            throw new IsDirectoryException(target);

        final UploadUploader uploader;
        try {
            uploader = client.files().uploadBuilder(target).withMode(WriteMode.OVERWRITE).start();
        } catch (DbxException e) {
            throw new DropBoxIOException(e);
        }

        return new DropBoxOutputStream(uploader);
    }

    @Nonnull
    @Override
    public DirectoryStream<Path> newDirectoryStream(final Path dir,
        final DirectoryStream.Filter<? super Path> filter)
        throws IOException
    {
        final String target = dir.toRealPath().toString();
        final Metadata dirMetadata = getMetadata(target);

        if (!(dirMetadata instanceof FolderMetadata))
            throw new NotDirectoryException(target);

        final List<Metadata> children;
        try {
            children = client.files().listFolder(target).getEntries();
        } catch (DbxException e) {
            throw new DropBoxIOException(e);
        }
        final List<Path> list = new ArrayList<>(children.size());

        for (final Metadata child: children)
            list.add(dir.resolve(child.getName()));

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
        // TODO: need a "shortcut" way for that; it's quite common
        final String target = dir.toRealPath().toString();

        try
        {
            // TODO: how to diagnose?
            CreateFolderResult folderV2 = client.files().createFolderV2(target);
            if (folderV2.getMetadata() == null)
                throw new DropBoxIOException("cannot create directory??");
        } catch (CreateFolderErrorException e) {
            if (e.errorValue.getPathValue().isNoWritePermission())
                throw new AccessDeniedException(target);
            //TODO: test for other errors
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
        }
    }

    @Override
    public void delete(final Path path)
        throws IOException
    {
        final String target = path.toRealPath().toString();
        final Metadata metadata = getMetadata(target);

        // TODO: metadata!
        try {
            if (metadata instanceof FolderMetadata && client.files().listFolder(target).getEntries().size() > 0)
                throw new DirectoryNotEmptyException(target);
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
        }

        try {
            client.files().deleteV2(target);
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
        }
    }

    @Override
    public void copy(final Path source, final Path target, final Set<CopyOption> options)
        throws IOException
    {
        final String srcPath = source.toRealPath().toString();
        final String dstPath = target.toRealPath().toString();

        Metadata dstMetadata = null;
        try {
            dstMetadata = getMetadata(dstPath);
        } catch (NoSuchFileException e) {
            //ignore
        }

        replaceExisting(dstPath, options, dstMetadata);

        try {
            // TODO: how to diagnose?
            RelocationResult relocationResult = client.files().copyV2(srcPath, dstPath);

            if (relocationResult == null)
                throw new DropBoxIOException("cannot copy??");
        } catch (DbxException e) {
            throw new DropBoxIOException(e);
        }
    }

    private void replaceExisting(String destination, Set<CopyOption> options, Metadata dstMetadata)
        throws DirectoryNotEmptyException, DropBoxIOException, FileAlreadyExistsException
    {
        if (dstMetadata != null) {
            if (options.contains(StandardCopyOption.REPLACE_EXISTING)) {
                try {
                    if (dstMetadata instanceof FolderMetadata && client.files().listFolder(destination).getEntries().size() > 0)
                        throw new DirectoryNotEmptyException(destination);

                    client.files().deleteV2(destination);
                } catch (DbxException e) {
                    throw DropBoxIOException.wrap(e);
                }
            } else {
                throw new FileAlreadyExistsException(destination);
            }
        }
    }

    @Override
    public void move(final Path source, final Path target, final Set<CopyOption> options)
        throws IOException
    {
        final String srcPath = source.toRealPath().toString();
        final String dstPath = target.toRealPath().toString();

        Metadata dstMetadata = null;
        try {
            dstMetadata = getMetadata(dstPath);
        } catch (NoSuchFileException e) {
            //ignore
        }

        replaceExisting(dstPath, options, dstMetadata);

        try {
            client.files().moveV2(srcPath, dstPath);
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

        final Metadata metadata = getMetadata(target);

        if (metadata == null)
            throw new NoSuchFileException(target);

        if (!(metadata instanceof FileMetadata))
            return;

        // TODO: assumed; not a file == directory
        for (final AccessMode mode: modes)
            if (mode == AccessMode.EXECUTE)
                throw new AccessDeniedException(target);
    }

    private Metadata getMetadata(String target) throws NoSuchFileException, DropBoxIOException
    {
        Metadata metadata;
        try {
            metadata = client.files().getMetadata(target);
        } catch (GetMetadataErrorException e) {
            if (e.errorValue.isPath() && e.errorValue.getPathValue().isNotFound()) {
                throw new NoSuchFileException(target);
            } else {
                throw DropBoxIOException.wrap(e);
            }
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
        }
        return metadata;
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
            return client.files().getMetadata(path.toRealPath().toString());
        } catch (DbxException e) {
            throw DropBoxIOException.wrap(e);
        }
    }
}
