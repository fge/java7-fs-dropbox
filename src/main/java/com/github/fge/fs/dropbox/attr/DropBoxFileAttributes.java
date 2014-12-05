package com.github.fge.fs.dropbox.attr;

import com.dropbox.core.DbxEntry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
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
    implements BasicFileAttributes
{
    private static final FileTime UNIX_EPOCH = FileTime.fromMillis(0L);

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
     * Returns the time of last access.
     * <p> If the file system implementation does not support a time stamp
     * to indicate the time of last access then this method returns
     * an implementation specific default value, typically the {@link
     * #lastModifiedTime() last-modified-time} or a {@code FileTime}
     * representing the epoch (1970-01-01T00:00:00Z).
     *
     * @return a {@code FileTime} representing the time of last access
     */
    @Override
    public FileTime lastAccessTime()
    {
        // No support for last access time, it seemed... Therefore:
        return UNIX_EPOCH;
    }

    /**
     * Returns the creation time. The creation time is the time that the file
     * was created.
     * <p> If the file system implementation does not support a time stamp
     * to indicate the time when the file was created then this method returns
     * an implementation specific default value, typically the {@link
     * #lastModifiedTime() last-modified-time} or a {@code FileTime}
     * representing the epoch (1970-01-01T00:00:00Z).
     *
     * @return a {@code FileTime} representing the time the file was created
     */
    @Override
    public FileTime creationTime()
    {
        // No support for creation time, therefore...
        return UNIX_EPOCH;
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
     * Tells whether the file is a symbolic link.
     */
    @Override
    public boolean isSymbolicLink()
    {
        return false;
    }

    /**
     * Tells whether the file is something other than a regular file, directory,
     * or symbolic link.
     */
    @Override
    public boolean isOther()
    {
        return false;
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

    /**
     * Returns an object that uniquely identifies the given file, or {@code
     * null} if a file key is not available. On some platforms or file systems
     * it is possible to use an identifier, or a combination of identifiers to
     * uniquely identify a file. Such identifiers are important for operations
     * such as file tree traversal in file systems that support <a
     * href="../package-summary.html#links">symbolic links</a> or file systems
     * that allow a file to be an entry in more than one directory. On UNIX file
     * systems, for example, the <em>device ID</em> and <em>inode</em> are
     * commonly used for such purposes.
     * <p> The file key returned by this method can only be guaranteed to be
     * unique if the file system and files remain static. Whether a file system
     * re-uses identifiers after a file is deleted is implementation
     * dependent and
     * therefore unspecified.
     * <p> File keys returned by this method can be compared for equality and
     * are
     * suitable for use in collections. If the file system and files remain
     * static,
     * and two files are the {@link Files#isSameFile same} with
     * non-{@code null} file keys, then their file keys are equal.
     *
     * @see Files#walkFileTree
     */
    @Override
    public Object fileKey()
    {
        return null;
    }
}
