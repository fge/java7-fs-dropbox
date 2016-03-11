package com.github.fge.fs.dropbox.misc;

import com.dropbox.core.DbxException;

import java.io.Closeable;
import java.io.IOException;

/**
 * Class used as a wrapper over the DropBox API's unchecked exceptions when
 * an {@link IOException} is needed
 *
 * <p>The problem mainly comes from {@link DbxClient.Uploader} and {@link
 * DbxClient.Uploader}. Both of these methods define a {@code close()} method
 * but none of them implement {@link Closeable}. Worse than that, at least as
 * far as the uploader is concerned, this method is not even idempotent, and all
 * exceptions it throws are <strong>unchecked</strong>.</p>
 *
 * <p>The file system API, however, needs "correct" {@link IOException}s to be
 * thrown. We therefore capture all {@link RuntimeException}s thrown by either a
 * downloader or an uploader, and wrap it into such an exception.</p>
 *
 * @see DropBoxInputStream
 * @see DropBoxOutputStream
 */
@SuppressWarnings({ "UnusedDeclaration", "serial" })
public final class DropBoxIOException
    extends IOException
{
    public static DropBoxIOException wrap(final DbxException e)
    {
        return new DropBoxIOException("Dropbox API error", e);
    }

	/**
     * Constructs an {@code IOException} with {@code null}
     * as its error detail message.
     */
    public DropBoxIOException()
    {
    }

    /**
     * Constructs an {@code IOException} with the specified detail message.
     *
     * @param message The detail message (which is saved for later retrieval
     * by the {@link #getMessage()} method)
     */
    public DropBoxIOException(final String message)
    {
        super(message);
    }

    /**
     * Constructs an {@code IOException} with the specified detail message
     * and cause.
     * <p> Note that the detail message associated with {@code cause} is
     * <i>not</i> automatically incorporated into this exception's detail
     * message.
     *
     * @param message The detail message (which is saved for later retrieval
     * by the {@link #getMessage()} method)
     * @param cause The cause (which is saved for later retrieval by the
     * {@link #getCause()} method).  (A null value is permitted,
     * and indicates that the cause is nonexistent or unknown.)
     * @since 1.6
     */
    public DropBoxIOException(final String message, final Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Constructs an {@code IOException} with the specified cause and a
     * detail message of {@code (cause==null ? null : cause.toString())}
     * (which typically contains the class and detail message of {@code cause}).
     * This constructor is useful for IO exceptions that are little more
     * than wrappers for other throwables.
     *
     * @param cause The cause (which is saved for later retrieval by the
     * {@link #getCause()} method).  (A null value is permitted,
     * and indicates that the cause is nonexistent or unknown.)
     * @since 1.6
     */
    public DropBoxIOException(final Throwable cause)
    {
        super(cause);
    }
}
