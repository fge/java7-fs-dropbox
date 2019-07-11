package com.github.fge.fs.dropbox.provider;

import com.github.fge.filesystem.provider.FileSystemProviderBase;

public final class DropBoxFileSystemProvider
    extends FileSystemProviderBase
{
    public static final String PARAM_ID = "id";

    public static final String ENV_CREDENTIAL = "credential";

    public DropBoxFileSystemProvider()
    {
        super(new DropBoxFileSystemRepository());
    }
}
