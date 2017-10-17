package com.github.fge.fs.dropbox;

import com.dropbox.core.util.IOUtil;
import com.github.fge.filesystem.exceptions.ReadOnlyAttributeException;
import com.github.fge.filesystem.provider.FileSystemRepository;
import com.github.fge.fs.dropbox.misc.DropBoxIOException;
import com.github.fge.fs.dropbox.provider.DropBoxFileSystemProvider;
import com.github.fge.fs.dropbox.provider.DropBoxFileSystemRepository;
import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FileSystemTest
{
    private static final String ACCESS_TOKEN = "yourAccessTokenHere";
    private static FileSystemProvider provider;
    private static URI uri;
    private static Map<String, String> env;
    private static String testDirectoryPath;


    @BeforeClass
    public static void setUpClass() throws Exception
    {
        uri = URI.create("dropbox://foo/");
        env = new HashMap<>();
        env.put("accessToken", ACCESS_TOKEN);

        final FileSystemRepository repository = new DropBoxFileSystemRepository();
        provider = new DropBoxFileSystemProvider(repository);

        testDirectoryPath = "/test-" + UUID.randomUUID().toString();

        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env))
        {
            Files.createDirectory(dropboxfs.getPath(testDirectoryPath));
        }
    }

    @BeforeMethod
    public void setUp() throws Exception
    {
    }

    @AfterClass
    public static void tearDown() throws Exception
    {
        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env))
        {
            deleteRecursive(dropboxfs, dropboxfs.getPath(testDirectoryPath));
        }
    }

    private static void deleteRecursive(FileSystem fs, Path directoryPath) throws IOException
    {
        if (Files.exists(directoryPath))
        {
            try (DirectoryStream<Path> paths= Files.newDirectoryStream(directoryPath))
            {
                for (Path path : paths)
                {
                    if (Files.isDirectory(path))
                    {
                        deleteRecursive(fs, path);
                    }
                    else
                    {
                        Files.delete(fs.getPath(path.toString()));
                    }
                }
            }
            Files.delete(fs.getPath(directoryPath.toString()));
        }
    }

    @Test(expectedExceptions = DropBoxIOException.class)
    public void testDirectoryStreamRoot() throws IOException
    {
        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env))
        {
            Files.newDirectoryStream(dropboxfs.getPath(""));
        }
    }

    @Test
    public void testDirectoryStream() throws IOException
    {
        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env))
        {
            Files.newDirectoryStream(dropboxfs.getPath(testDirectoryPath));
        }
    }

    @Test
    public void testCreateFile() throws IOException
    {
        boolean thrown = false;
        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env))
        {
            Path path = dropboxfs.getPath(testDirectoryPath + "/create_test");
            Files.createFile(path);
        }
        catch (UnsupportedOperationException e)
        {
            thrown = true;
        }
        Assert.assertTrue(thrown);
    }

    @Test
    public void testCreateDirectory() throws IOException
    {
        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env))
        {
            for (int i = 1; i <= 5; i++)
            {
                Path path = dropboxfs.getPath(testDirectoryPath + "/create_directory_test-" + i);
                Path file = Files.createDirectory(path);
                Assert.assertNotNull(file);
                Assert.assertTrue(Files.exists(path));
            }
        }
    }

    @Test
    public void testCreateDirectories() throws IOException
    {
        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env))
        {
            for (int i = 1; i <= 5; i++)
            {
                Path path = dropboxfs.getPath(testDirectoryPath + "/create_directories_test-" + i + "/sub_dir-" + i);
                Path file = Files.createDirectories(path);
                Assert.assertNotNull(file);
                Assert.assertTrue(Files.exists(path));
            }
        }
    }

    @Test
    public void testCreateDirectoryAlreadyExists() throws Exception
    {
        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env))
        {
            Path path = dropboxfs.getPath(testDirectoryPath + "/create_directory_already_exists_test");
            Files.createDirectory(path);

            boolean thrown = false;
            try {
                Files.createDirectory(path);
            } catch (FileAlreadyExistsException e) {
                thrown = true;
            }
            Assert.assertTrue(thrown);
        }
    }

    @Test
    public void testCopy() throws IOException
    {
        try
        (
            final FileSystem memfs = MemoryFileSystemBuilder.newEmpty().build("test");
            final FileSystem dropboxfs = provider.newFileSystem(uri, env)
        )
        {
            String contentOriginal = "This will be copied";
            String contentCopy;

            //create the source file in memory
            Path source = memfs.getPath("/copy.txt");
            Files.createFile(source);
            Files.write(source, contentOriginal.getBytes());

            //create the destination on Dropbox
            Path dest = dropboxfs.getPath(testDirectoryPath + "/copy.txt");

            //copy to Dropbox
            Files.copy(source, dest);
            Assert.assertTrue(Files.exists(dest));
            Assert.assertTrue(Files.isRegularFile(dest));
            try (InputStream in = Files.newInputStream(dest))
            {
                contentCopy = IOUtil.toUtf8String(in); //inputstream, SeekableByteChannel not supported
            }
            Assert.assertEquals(contentOriginal, contentCopy);

            Files.delete(source);

            //copy from Dropbox
            Files.copy(dest, source);
            Assert.assertTrue(Files.exists(source));
            Assert.assertTrue(Files.isRegularFile(source));
            contentCopy = new String(Files.readAllBytes(source));
            Assert.assertEquals(contentOriginal, contentCopy);
        }
    }

    @Test
    public void testCopyAlreadyExists() throws IOException
    {
        try
            (
                final FileSystem memfs = MemoryFileSystemBuilder.newEmpty().build("test");
                final FileSystem dropboxfs = provider.newFileSystem(uri, env)
            )
        {
            Path source = memfs.getPath("/copy.txt");
            Files.createFile(source);
            Path dest = dropboxfs.getPath(testDirectoryPath + "/copy_already_exists.txt");

            boolean thrown = false;

            Files.copy(source, dest);
            try
            {
                Files.copy(source, dest);
            } catch (FileAlreadyExistsException e) {
                thrown = true;
            }
            Assert.assertTrue(thrown);
        }
    }

    @Test
    public void testCheckAccessFile() throws IOException
    {
        try (final FileSystem memfs = MemoryFileSystemBuilder.newEmpty().build("test");
             final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            Path source = memfs.getPath("/file.txt");
            Files.createFile(source);
            Path fileExists = dropboxfs.getPath(testDirectoryPath + "/checkAccess_file_exists.txt");
            Files.copy(source, fileExists);

            boolean thrown = false;

            dropboxfs.provider().checkAccess(fileExists);
            dropboxfs.provider().checkAccess(fileExists, AccessMode.READ);
            dropboxfs.provider().checkAccess(fileExists, AccessMode.WRITE);
            try {
                dropboxfs.provider().checkAccess(fileExists, AccessMode.EXECUTE);
            } catch (AccessDeniedException e) {
                thrown = true;
            }
            Assert.assertTrue(thrown);
        }
    }

    @Test
    public void testCheckAccessNoSuchFile() throws IOException
    {
        try (final FileSystem memfs = MemoryFileSystemBuilder.newEmpty().build("test");
             final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            Path noSuchFile = dropboxfs.getPath(testDirectoryPath + "/checkAccess_file_not_found.txt");

            boolean thrown = false;

            try {
                dropboxfs.provider().checkAccess(noSuchFile);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertTrue(thrown);
            thrown = false;
            try {
                dropboxfs.provider().checkAccess(noSuchFile, AccessMode.READ);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertTrue(thrown);
            thrown = false;
            try {
                dropboxfs.provider().checkAccess(noSuchFile, AccessMode.WRITE);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertTrue(thrown);
            thrown = false;
            try {
                dropboxfs.provider().checkAccess(noSuchFile, AccessMode.EXECUTE);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertTrue(thrown);
        }
    }


    @Test
    public void testCheckAccessDirectory() throws IOException
    {
        try (final FileSystem memfs = MemoryFileSystemBuilder.newEmpty().build("test");
             final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            Path directory = Files.createDirectory(dropboxfs.getPath(testDirectoryPath + "/checkAccess_directory"));

            boolean thrown = false;

            try {
                dropboxfs.provider().checkAccess(directory);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertFalse(thrown);
            thrown = false;
            try {
                dropboxfs.provider().checkAccess(directory, AccessMode.READ);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertFalse(thrown);
            thrown = false;
            try {
                dropboxfs.provider().checkAccess(directory, AccessMode.WRITE);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertFalse(thrown);
            thrown = false;
            try {
                dropboxfs.provider().checkAccess(directory, AccessMode.EXECUTE);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertFalse(thrown);
        }
    }

    @Test
    public void testMoveDirectory() throws IOException
    {
        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env))
        {
            Path sourcePath = Files.createDirectory(dropboxfs.getPath(testDirectoryPath + "/moveDirectory_dir_source"));
            Path targetPath = dropboxfs.getPath(testDirectoryPath + "/moveDirectory_dir_target");

            Assert.assertFalse(Files.exists(targetPath));
            Files.move(sourcePath, targetPath);
            Assert.assertTrue(Files.exists(targetPath));
        }
    }

    @Test(expectedExceptions = FileAlreadyExistsException.class)
    public void testMoveDirectoryAlreadyExists() throws IOException
    {
        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env))
        {
            Path sourcePath = Files.createDirectory(dropboxfs.getPath(testDirectoryPath + "/moveDirectory_dir_already_exists_source"));
            Path targetPath = Files.createDirectory(dropboxfs.getPath(testDirectoryPath + "/moveDirectory_dir_already_exists_target"));

            Files.move(sourcePath, targetPath);
        }
    }

    @Test
    public void testMoveDirectoryReplaceExisting() throws IOException
    {
        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            Path sourcePath = Files.createDirectory(dropboxfs.getPath(testDirectoryPath + "/moveDirectory_dir_replace_existing_source"));
            Files.createDirectory(dropboxfs.getPath(testDirectoryPath + "/moveDirectory_dir_replace_existing_source/sub-dir"));
            Path targetPath = Files.createDirectory(dropboxfs.getPath(testDirectoryPath + "/moveDirectory_dir_replace_existing_target"));
            Path targetSubdirectoryPath = dropboxfs.getPath(testDirectoryPath + "/moveDirectory_dir_replace_existing_target/sub-dir");

            Assert.assertFalse(Files.exists(targetSubdirectoryPath));
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            Assert.assertTrue(Files.exists(targetSubdirectoryPath));
        }
    }

    @Test
    public void testMoveFile() throws IOException
    {
        try (final FileSystem memfs = MemoryFileSystemBuilder.newEmpty().build("test");
             final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            Path source = Files.createFile(memfs.getPath("file.txt"));

            Path dropboxSource = dropboxfs.getPath(testDirectoryPath + "/testMoveFile_file.txt");
            Path dropboxTarget = dropboxfs.getPath(testDirectoryPath + "/testMoveFile_renamed_file.txt");

            Files.copy(source, dropboxSource);
            Assert.assertTrue(Files.exists(dropboxSource));

            Files.move(dropboxSource, dropboxTarget);
            Assert.assertTrue(Files.exists(dropboxTarget));
        }
    }

    @Test(expectedExceptions = FileAlreadyExistsException.class)
    public void testMoveFileAlreadyExists() throws IOException
    {
        try (final FileSystem memfs = MemoryFileSystemBuilder.newEmpty().build("test");
             final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            Path source = Files.createFile(memfs.getPath("file.txt"));

            Path dropboxSource = dropboxfs.getPath(testDirectoryPath + "/moveFile_file_already_exists_source.txt");
            Path dropboxTarget = dropboxfs.getPath(testDirectoryPath + "/moveFile_file_already_exists_target.txt");

            Files.copy(source, dropboxSource);
            Files.copy(dropboxSource, dropboxTarget);
            Files.move(dropboxSource, dropboxTarget);
        }
    }

    @Test
    public void testMoveFileReplaceExisting() throws IOException
    {
        try (final FileSystem memfs = MemoryFileSystemBuilder.newEmpty().build("test");
             final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            Path source = Files.createFile(memfs.getPath("file.txt"));

            Path dropboxSource = dropboxfs.getPath(testDirectoryPath + "/moveFile_file_replace_existing_source.txt");
            Path dropboxTarget = dropboxfs.getPath(testDirectoryPath + "/moveFile_file_replace_existing_target.txt");

            Files.copy(source, dropboxSource);
            Files.copy(dropboxSource, dropboxTarget);
            Files.move(dropboxSource, dropboxTarget, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test(expectedExceptions = ReadOnlyAttributeException.class)
    public void testMoveFileToForeignTarget() throws IOException
    {
        try (final FileSystem memfs = MemoryFileSystemBuilder.newEmpty().build("test");
             final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            Path source = Files.createFile(memfs.getPath("file.txt"));
            Path target = dropboxfs.getPath(testDirectoryPath + "/move_file_to_foreign_target_target.txt");

            Files.move(source, target); // TODO: BasicFileAttributesProvider is readonly. I don't know if it has to be
        }
    }

}
