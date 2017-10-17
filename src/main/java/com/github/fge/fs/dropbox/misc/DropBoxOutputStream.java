package com.github.fge.fs.dropbox.misc;

import com.dropbox.core.DbxException;
import com.dropbox.core.v1.DbxClientV1;
import com.dropbox.core.v2.files.UploadUploader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper over {@link DbxClientV1.Uploader} extending {@link OutputStream}
 *
 * <p>This class wraps a DropBox downloader class by extending {@code
 * InputStream} and delegating all of its methods to the downloader's
 * included stream. As such, this means this class is usable in a
 * try-with-resources statement (which the DropBox class isn't).</p>
 *
 * <p>Note about exception handling: unfortunately, the DropBox API class used
 * to wrap an output stream defines a close method which is not declared to
 * throw an exception; which means it may throw none, or it may throw an
 * <em>unchecked</em> exception. As such, the {@link #close()} method of this
 * class captures all {@link RuntimeException}s which {@link
 * DbxClientV1.Uploader#close()} may throw and wrap it into a {@link
 * DropBoxIOException}. If the underlying output stream <em>did</em> throw an
 * exception, however, then such an exception is {@link
 * Throwable#addSuppressed(Throwable) suppressed}.</p>
 */
@SuppressWarnings("HtmlTagCanBeJavadocTag")
// TODO: more complex than the input stuff; check again (.abort(), etc)
public final class DropBoxOutputStream
    extends OutputStream
{
    private final AtomicBoolean closeCalled = new AtomicBoolean(false);

    private final UploadUploader uploader;
    private final OutputStream out;

    public DropBoxOutputStream(@Nonnull final UploadUploader uploader)
    {

        this.uploader = Objects.requireNonNull(uploader);
        out = uploader.getOutputStream();
    }

    @Override
    public void write(final int b)
        throws IOException
    {
        out.write(b);
    }

    @Override
    public void write(final byte[] b)
        throws IOException
    {
        out.write(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len)
        throws IOException
    {
        out.write(b, off, len);
    }

    @Override
    public void flush()
        throws IOException
    {
        out.flush();
    }

    @Override
    public void close()
        throws IOException
    {
        /*
         * Reentrancy: check if .close() has been called already...
         */
        if (closeCalled.getAndSet(true))
            return;

        /*
         * TODO: UGLY! Tied to the API in a big, big way
         */
        IOException exception = null;
        boolean finishedOK = true;

        /*
         * First try and close the stream
         */
        try {
            out.close();
        } catch (IOException e) {
            exception = e;
        }

        /*
         * First, .finish() the transaction; if this throws an exception, wrap
         * it in either the exception thrown by the OutputStream, or a new
         * DropBoxIOException if the stream was OK.
         */

        try {
            uploader.finish();
        } catch (DbxException e) {
            finishedOK = false;
            if (exception == null)
                exception = new DropBoxIOException(e);
            else
                exception.addSuppressed(e);
        }

        /*
         * Now try and .close(). If the finish stage was OK, ignore; if not,
         * suppress the returned exception (we always have a nonnull exception).
         */

        try {
            uploader.close();
        } catch (RuntimeException e) {
            if (!finishedOK)
                exception.addSuppressed(e);
        }

        if (exception != null)
            throw exception;
    }
}
