package com.github.fge.fs.dropbox.filestore;

import com.dropbox.core.DbxAccountInfo;
import com.dropbox.core.DbxAccountInfo.Quota;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxException;
import com.github.fge.filesystem.filestore.FileStoreBase;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;

/**
 * A simple DropBox {@link FileStore}
 *
 * <p>This makes use of information available in {@link DbxAccountInfo.Quota}.
 * Information is computed in "real time".</p>
 */
public final class DropBoxFileStore
    extends FileStoreBase
{
    private final DbxClient client;

    /**
     * Constructor
     *
     * @param client the (valid) DropBox client to use
     */
    public DropBoxFileStore(final DbxClient client)
    {
        super("dropbox", false);
        this.client = client;
    }

    /**
     * Returns the size, in bytes, of the file store.
     *
     * @return the size of the file store, in bytes
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long getTotalSpace()
        throws IOException
    {
        return getQuota().total;
    }

    /**
     * Returns the number of bytes available to this Java virtual machine on the
     * file store.
     * <p> The returned number of available bytes is a hint, but not a
     * guarantee, that it is possible to use most or any of these bytes.  The
     * number of usable bytes is most likely to be accurate immediately
     * after the space attributes are obtained. It is likely to be made
     * inaccurate
     * by any external I/O operations including those made on the system outside
     * of this Java virtual machine.
     *
     * @return the number of bytes available
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long getUsableSpace()
        throws IOException
    {
        final Quota quota = getQuota();
        return quota.total - quota.normal;
    }

    /**
     * Returns the number of unallocated bytes in the file store.
     * <p> The returned number of unallocated bytes is a hint, but not a
     * guarantee, that it is possible to use most or any of these bytes.  The
     * number of unallocated bytes is most likely to be accurate immediately
     * after the space attributes are obtained. It is likely to be
     * made inaccurate by any external I/O operations including those made on
     * the system outside of this virtual machine.
     *
     * @return the number of unallocated bytes
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long getUnallocatedSpace()
        throws IOException
    {
        final Quota quota = getQuota();
        return quota.total - quota.normal;
    }

    /**
     * Tells whether or not this file store supports the file attributes
     * identified by the given file attribute view.
     * <p> Invoking this method to test if the file store supports {@link
     * BasicFileAttributeView} will always return {@code true}. In the case of
     * the default provider, this method cannot guarantee to give the correct
     * result when the file store is not a local storage device. The reasons for
     * this are implementation specific and therefore unspecified.
     *
     * @param type the file attribute view type
     * @return {@code true} if, and only if, the file attribute view is
     * supported
     */
    @Override
    public boolean supportsFileAttributeView(
        final Class<? extends FileAttributeView> type)
    {
        return type == BasicFileAttributeView.class;
    }

    /**
     * Tells whether or not this file store supports the file attributes
     * identified by the given file attribute view.
     * <p> Invoking this method to test if the file store supports {@link
     * BasicFileAttributeView}, identified by the name "{@code basic}" will
     * always return {@code true}. In the case of the default provider, this
     * method cannot guarantee to give the correct result when the file store is
     * not a local storage device. The reasons for this are implementation
     * specific and therefore unspecified.
     *
     * @param name the {@link FileAttributeView#name name} of file attribute
     * view
     * @return {@code true} if, and only if, the file attribute view is
     * supported
     */
    @Override
    public boolean supportsFileAttributeView(final String name)
    {
        return "basic".equals(name);
    }

    private Quota getQuota()
        throws IOException
    {
        try {
            return client.getAccountInfo().quota;
        } catch (DbxException e) {
            throw new IOException("cannot get quota info from account", e);
        }
    }
}
