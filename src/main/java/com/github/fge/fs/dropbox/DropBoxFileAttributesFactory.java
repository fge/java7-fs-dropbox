package com.github.fge.fs.dropbox;

import com.dropbox.core.v2.files.Metadata;
import com.github.fge.filesystem.attributes.FileAttributesFactory;

public final class DropBoxFileAttributesFactory
    extends FileAttributesFactory
{
    public DropBoxFileAttributesFactory()
    {
        setMetadataClass(Metadata.class);
        addImplementation("basic", DropBoxBasicFileAttributesProvider.class);
    }
}
