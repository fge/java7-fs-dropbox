package com.github.fge.fs.dropbox;

import com.github.fge.filesystem.provider.FileSystemProviderBase;

public final class DropBoxFileSystemProvider
    extends FileSystemProviderBase
{
    public static final String PARAM_ID = "id";

    public static final String ENV_USER_CREDENTIAL = "user_credential";

    public static final String ENV_APP_CREDENTIAL = "app_credential";

    public DropBoxFileSystemProvider()
    {
        super(new DropBoxFileSystemRepository());
    }
}
