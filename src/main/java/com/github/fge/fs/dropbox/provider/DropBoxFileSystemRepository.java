package com.github.fge.fs.dropbox.provider;

import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxRequestConfig;
import com.github.fge.filesystem.driver.FileSystemDriver;
import com.github.fge.filesystem.provider.FileSystemRepositoryBase;
import com.github.fge.fs.dropbox.driver.DropBoxFileSystemDriver;
import com.github.fge.fs.dropbox.filestore.DropBoxFileStore;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;

@ParametersAreNonnullByDefault
public final class DropBoxFileSystemRepository
    extends FileSystemRepositoryBase
{
    private static final String NAME = "java7-fs-dropbox";
    private static final String LOCALE = Locale.US.toString();

    public DropBoxFileSystemRepository()
    {
        super("dropbox", new DropboxFileSystemFactoryProvider());
    }

    @Nonnull
    @Override
    public FileSystemDriver createDriver(final URI uri,
        final Map<String, ?> env)
        throws IOException
    {
        final String accessToken = (String) env.get("accessToken");
        if (accessToken == null)
            throw new IllegalArgumentException("access token not found");

        final DbxRequestConfig config = new DbxRequestConfig(NAME, LOCALE);
        final DbxClient client = new DbxClient(config, accessToken);
        final DropBoxFileStore fileStore
            = new DropBoxFileStore(client,
            factoryProvider.getAttributesFactory());
        return new DropBoxFileSystemDriver(fileStore, factoryProvider, client);
    }
}
