package com.github.fge.fs.dropbox.provider;

import com.github.fge.filesystem.provider.FileSystemProviderBase;

public final class DropBoxFileSystemProvider
    extends FileSystemProviderBase
{
    public DropBoxFileSystemProvider()
    {
        super(new DropBoxFileSystemRepository());
    }
}
