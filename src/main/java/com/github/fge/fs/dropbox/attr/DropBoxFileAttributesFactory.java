package com.github.fge.fs.dropbox.attr;

import com.dropbox.core.DbxEntry;
import com.github.fge.filesystem.attributes.FileAttributesFactory;

public final class DropBoxFileAttributesFactory
    extends FileAttributesFactory
{
    public DropBoxFileAttributesFactory()
    {
        setMetadataClass(DbxEntry.class);
        addImplementation("basic", DropBoxBasicFileAttributesProvider.class);
    }
}
