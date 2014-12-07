package com.github.fge.fs.dropbox.attr;

import com.dropbox.core.DbxEntry;
import com.github.fge.filesystem.attrs.BasicFileAttributesBase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

/**
 * {@link BasicFileAttributes} implementation for DropBox
 *
 * <p>Note: DropBox has poor support for file times; as required by the {@link
 * BasicFileAttributes} contract, all methods returning a {@link FileTime} for
 * which there is no support will return  Unix's epoch (that is, Jan 1st, 1970
 * at 00:00:00 GMT).</p>
 */
public final class DropBoxFileAttributes
    extends BasicFileAttributesBase
{
    @Nullable
    private final DbxEntry.File fileEntry;

    public DropBoxFileAttributes(@Nonnull final DbxEntry entry)
    {
        fileEntry = Objects.requireNonNull(entry).isFolder()
            ? entry.asFile() : null;
    }

    /**
     * Returns the time of last modification.
     * <p> If the file system implementation does not support a time stamp
     * to indicate the time of last modification then this method returns an
     * implementation specific default value, typically a {@code FileTime}
     * representing the epoch (1970-01-01T00:00:00Z).
     *
     * @return a {@code FileTime} representing the time the file was last
     * modified
     */
    @Override
    public FileTime lastModifiedTime()
    {
        return fileEntry == null ? UNIX_EPOCH
            : FileTime.fromMillis(fileEntry.lastModified.getTime());
    }

    /**
     * Tells whether the file is a regular file with opaque content.
     */
    @Override
    public boolean isRegularFile()
    {
        return fileEntry != null;
    }

    /**
     * Tells whether the file is a directory.
     */
    @Override
    public boolean isDirectory()
    {
        return fileEntry == null;
    }

    /**
     * Returns the size of the file (in bytes). The size may differ from the
     * actual size on the file system due to compression, support for sparse
     * files, or other reasons. The size of files that are not {@link
     * #isRegularFile regular} files is implementation specific and
     * therefore unspecified.
     *
     * @return the file size, in bytes
     */
    @Override
    public long size()
    {
        return fileEntry == null ? 0L : fileEntry.numBytes;
    }
}
