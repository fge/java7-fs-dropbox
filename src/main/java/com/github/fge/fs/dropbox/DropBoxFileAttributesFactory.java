package com.github.fge.fs.dropbox;

import com.dropbox.core.v2.files.Metadata;
import com.github.fge.filesystem.driver.ExtendedFileSystemDriverBase.ExtendsdFileAttributesFactory;

public final class DropBoxFileAttributesFactory
    extends ExtendsdFileAttributesFactory
{
    public DropBoxFileAttributesFactory()
    {
        setMetadataClass(Metadata.class);
        addImplementation("basic", DropBoxBasicFileAttributesProvider.class);
    }
}
