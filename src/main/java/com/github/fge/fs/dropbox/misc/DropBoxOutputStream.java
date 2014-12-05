package com.github.fge.fs.dropbox.misc;

import com.dropbox.core.DbxClient;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Wrapper over {@link DbxClient.Uploader} extending {@link OutputStream}
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
 * DbxClient.Uploader#close()} may throw and wrap it into a {@link
 * DropBoxIOException}. If the underlying output stream <em>did</em> throw an
 * exception, however, then such an exception is {@link
 * Throwable#addSuppressed(Throwable) suppressed}.</p>
 */
@SuppressWarnings("HtmlTagCanBeJavadocTag")
// TODO: more complex than the input stuff; check again (.abort(), etc)
public final class DropBoxOutputStream
    extends OutputStream
{
    private final DbxClient.Uploader uploader;
    private final OutputStream out;

    public DropBoxOutputStream(@Nonnull final DbxClient.Uploader uploader)
    {
        this.uploader = Objects.requireNonNull(uploader);
        out = uploader.getBody();
    }

    /**
     * Writes the specified byte to this output stream. The general
     * contract for <code>write</code> is that one byte is written
     * to the output stream. The byte to be written is the eight
     * low-order bits of the argument <code>b</code>. The 24
     * high-order bits of <code>b</code> are ignored.
     * <p>
     * Subclasses of <code>OutputStream</code> must provide an
     * implementation for this method.
     *
     * @param      b   the <code>byte</code>.
     * @exception IOException  if an I/O error occurs. In particular,
     *             an <code>IOException</code> may be thrown if the
     *             output stream has been closed.
     */
    @Override
    public void write(final int b)
        throws IOException
    {
        out.write(b);
    }

    /**
     * Writes <code>b.length</code> bytes from the specified byte array
     * to this output stream. The general contract for <code>write(b)</code>
     * is that it should have exactly the same effect as the call
     * <code>write(b, 0, b.length)</code>.
     *
     * @param      b   the data.
     * @exception IOException  if an I/O error occurs.
     * @see        OutputStream#write(byte[], int, int)
     */
    @Override
    public void write(final byte[] b)
        throws IOException
    {
        out.write(b);
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array
     * starting at offset <code>off</code> to this output stream.
     * The general contract for <code>write(b, off, len)</code> is that
     * some of the bytes in the array <code>b</code> are written to the
     * output stream in order; element <code>b[off]</code> is the first
     * byte written and <code>b[off+len-1]</code> is the last byte written
     * by this operation.
     * <p>
     * The <code>write</code> method of <code>OutputStream</code> calls
     * the write method of one argument on each of the bytes to be
     * written out. Subclasses are encouraged to override this method and
     * provide a more efficient implementation.
     * <p>
     * If <code>b</code> is <code>null</code>, a
     * <code>NullPointerException</code> is thrown.
     * <p>
     * If <code>off</code> is negative, or <code>len</code> is negative, or
     * <code>off+len</code> is greater than the length of the array
     * <code>b</code>, then an <tt>IndexOutOfBoundsException</tt> is thrown.
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @exception IOException  if an I/O error occurs. In particular,
     *             an <code>IOException</code> is thrown if the output
     *             stream is closed.
     */
    @Override
    public void write(final byte[] b, final int off, final int len)
        throws IOException
    {
        out.write(b, off, len);
    }

    /**
     * Flushes this output stream and forces any buffered output bytes
     * to be written out. The general contract of <code>flush</code> is
     * that calling it is an indication that, if any bytes previously
     * written have been buffered by the implementation of the output
     * stream, such bytes should immediately be written to their
     * intended destination.
     * <p>
     * If the intended destination of this stream is an abstraction provided by
     * the underlying operating system, for example a file, then flushing the
     * stream guarantees only that bytes previously written to the stream are
     * passed to the operating system for writing; it does not guarantee that
     * they are actually written to a physical device such as a disk drive.
     * <p>
     * The <code>flush</code> method of <code>OutputStream</code> does nothing.
     *
     * @exception IOException  if an I/O error occurs.
     */
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
        IOException exception = null;
        try {
            out.close();
        } catch (IOException e) {
            exception = e;
        }

        try {
            uploader.close();
        } catch (RuntimeException e) {
            if (exception != null)
                exception.addSuppressed(e);
            else
                exception = new IOException("tell me what to do, please", e);
        }

        if (exception != null)
            throw exception;
    }
}
