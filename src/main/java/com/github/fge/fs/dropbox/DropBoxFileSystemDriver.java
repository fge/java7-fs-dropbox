package com.github.fge.fs.dropbox;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.CopyOption;
import java.nio.file.FileStore;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxUploader;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.Metadata;
import com.github.fge.filesystem.driver.CachedFileSystemDriverBase;
import com.github.fge.filesystem.provider.FileSystemFactoryProvider;

import vavi.nio.file.Util;

import static vavi.nio.file.Util.toPathString;

@ParametersAreNonnullByDefault
public final class DropBoxFileSystemDriver
    extends CachedFileSystemDriverBase<Metadata> {

	private final DbxClientV2 client;

    @SuppressWarnings("unchecked")
    public DropBoxFileSystemDriver(final FileStore fileStore,
        final FileSystemFactoryProvider provider, final DbxClientV2 client, final Map<String, ?> env) {

    	super(fileStore, provider);
        this.client = client;
        ignoreAppleDouble = (Boolean) ((Map<String, Object>) env).getOrDefault("ignoreAppleDouble", Boolean.FALSE);
    }

    /** */
    private String toDbxPathString(Path path) throws IOException {
         String pathString = toPathString(path);
         return pathString.equals("/") ? "" : pathString;
    }

    @Override
    protected boolean isFolder(Metadata entry) {
        // ugly
        return FolderMetadata.class.isInstance(entry);
    }

    @Override
    protected String getFilenameString(Metadata entry) throws IOException {
    	return entry.getName();
    }

    @Override
    protected Metadata getRootEntry() throws IOException {
    	return new FolderMetadata("/", "0", "/", "/", null, null, null, null);
    }
    
    @Override
    protected Metadata getEntry(Metadata dirEntry, Path path) throws IOException {
    	try {
    		return client.files().getMetadata(toDbxPathString(path));
        } catch (GetMetadataErrorException e) {
            return null;
        } catch (DbxException e) {
            throw new IOException("path: " + path, e);
        }
    }

    @Override
    protected InputStream downloadEntry(Metadata entry, Path path, Set<? extends OpenOption> options) throws IOException {
        try {
	        final DbxDownloader<?> downloader = client.files().download(toDbxPathString(path), null);
	        return new BufferedInputStream(new Util.InputStreamForDownloading(downloader.getInputStream()) {
	            @Override
	            protected void onClosed() throws IOException {
	                downloader.close();
	            }
	        });
	    } catch (DbxException e) {
	        throw new IOException("path: " + path, e);
	    }
    }

    @Override
    protected OutputStream uploadEntry(Metadata parentEntry, Path path, Set<? extends OpenOption> options) throws IOException {
        try {
            final DbxUploader<?, ?, ?> uploader = client.files().upload(toDbxPathString(path));
            return new BufferedOutputStream(new Util.OutputStreamForUploading(uploader.getOutputStream()) {
                @Override
                protected void onClosed() throws IOException {
                    try {
                        FileMetadata newEntry = FileMetadata.class.cast(uploader.finish());
                        cache.addEntry(path, newEntry);
                    } catch (DbxException e) {
                        throw new IOException(e);
                    } finally {
                        uploader.close();
                    }
                }
            });
        } catch (DbxException e) {
            throw new IOException("path: " + path, e);
        }
    }
    
    @Override
    protected Metadata createDirectoryEntry(Path dir) throws IOException {
        try {
            // TODO: how to diagnose?
            return client.files().createFolderV2(toDbxPathString(dir)).getMetadata();
        } catch (DbxException e) {
            throw new IOException("dir: " + dir, e);
        }
    }

    @Override
    protected List<Metadata> getDirectoryEntries(Metadata dirEntry, Path dir) throws IOException {
        try {
        	return client.files().listFolder(toDbxPathString(dir)).getEntries();
        } catch (DbxException e) {
            throw new IOException("dir: " + dir, e);
        }
    }

    @Override
    protected boolean hasChildren(Metadata dirEntry, Path dir) throws IOException {
    	return getDirectoryEntries(dirEntry, dir).size() > 0;
    }

    /** */
    protected void removeEntry(Metadata entry, Path path) throws IOException {
        try {
	        // TODO: unknown what happens when a move operation is performed
	        // and the target already exists
	        client.files().deleteV2(toDbxPathString(path));
        } catch (DbxException e) {
            throw new IOException("path: " + path, e);
        }
    }

    /** */
    protected Metadata copyEntry(Metadata sourceEntry, Metadata targetParentEntry, Path source, Path target, Set<CopyOption> options) throws IOException {
        try {
        	return client.files().copyV2(toDbxPathString(source), toDbxPathString(target)).getMetadata();
        } catch (DbxException e) {
            throw new IOException("path: " + source + ", " + target, e);
        }
    }

    @Override
    protected Metadata moveEntry(Metadata sourceEntry, Metadata targetParentEntry, Path source, Path target, boolean targetIsParent) throws IOException {
        try {
        	String targetPathString = toDbxPathString(target);
        	return client.files().moveV2(toDbxPathString(source), targetPathString).getMetadata();
        } catch (DbxException e) {
            throw new IOException("path: " + source + ", " + target, e);
        }
    }

    @Override
    protected Metadata moveFolderEntry(Metadata sourceEntry, Metadata targetParentEntry, Path source, Path target, boolean targetIsParent) throws IOException {
        try {
        	String targetPathString = toDbxPathString(targetIsParent ? target.resolve(source.getFileName()) : target);
        	return client.files().moveV2(toDbxPathString(source), targetPathString).getMetadata();
        } catch (DbxException e) {
            throw new IOException("path: " + source + ", " + target, e);
        }
    }

    @Override
    protected Metadata renameEntry(Metadata sourceEntry, Metadata targetParentEntry, Path source, Path target) throws IOException {
        try {
        	return client.files().moveV2(toDbxPathString(source), toDbxPathString(target)).getMetadata();
        } catch (DbxException e) {
            throw new IOException("path: " + source + ", " + target, e);
        }
    }
}
