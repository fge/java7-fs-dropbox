package com.github.fge.fs.dropbox.provider;

import com.github.fge.filesystem.provider.FileSystemFactoryProvider;
import com.github.fge.fs.dropbox.attr.DropBoxFileAttributesFactory;

public final class DropboxFileSystemFactoryProvider
    extends FileSystemFactoryProvider
{
    public DropboxFileSystemFactoryProvider()
    {
        setAttributesFactory(new DropBoxFileAttributesFactory());
    }
}
