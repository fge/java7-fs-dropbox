package com.github.fge.fs.dropbox;

import com.github.fge.filesystem.provider.FileSystemFactoryProvider;

public final class DropboxFileSystemFactoryProvider
    extends FileSystemFactoryProvider
{
    public DropboxFileSystemFactoryProvider()
    {
        setAttributesFactory(new DropBoxFileAttributesFactory());
        setOptionsFactory(new DropBoxFileSystemOptionsFactory());
    }
}
