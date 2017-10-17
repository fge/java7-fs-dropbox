package com.github.fge.fs.dropbox.attr;

import com.dropbox.core.util.LangUtil;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.attribute.FileTime;
import java.util.Date;

public class DropBoxBasicFileAttributesProviderTest
{
//    private DropBoxBasicFileAttributesProvider nullProvider;
    private DropBoxBasicFileAttributesProvider fileProvider;
    private DropBoxBasicFileAttributesProvider folderProvider;

    private Date date;
    private long size;

    @BeforeMethod
    public void setUp() throws Exception
    {
//        nullProvider = new DropBoxBasicFileAttributesProvider(null); //not allowed (@Nonnull)

        date = new Date();
        size = 1L;
        FileMetadata fileMetadata = FileMetadata.newBuilder("mockFile", "id", date, date, "123456789", size).build();
        fileProvider = new DropBoxBasicFileAttributesProvider(fileMetadata);

        FolderMetadata folderMetadata = FolderMetadata.newBuilder("mockFolder", "id").build();
        folderProvider = new DropBoxBasicFileAttributesProvider(folderMetadata);
    }

    @AfterMethod
    public void tearDown() throws Exception
    {
    }

//    @Test
//    public void testLastModifiedTimeNull() throws Exception
//    {
//        Assert.assertTrue(nullProvider.lastModifiedTime(), FileTime.fromMillis(0L));
//    }

    @Test
    public void testLastModifiedTimeFile() throws Exception
    {
        Assert.assertEquals(fileProvider.lastModifiedTime(), FileTime.fromMillis(LangUtil.truncateMillis(this.date).getTime()));
    }

    @Test
    public void testLastModifiedTimeFolder() throws Exception
    {
        Assert.assertEquals(folderProvider.lastModifiedTime(), FileTime.fromMillis(0L));
    }

//    @Test
//    public void testIsRegularFileNull() throws Exception
//    {
//        Assert.assertFalse(nullProvider.isRegularFile());
//    }

    @Test
    public void testIsRegularFileFile() throws Exception
    {
        Assert.assertTrue(fileProvider.isRegularFile());
    }

    @Test
    public void testIsRegularFileFolder() throws Exception
    {
        Assert.assertFalse(folderProvider.isRegularFile());
    }

//    @Test
//    public void testIsDirectoryNull() throws Exception
//    {
//        Assert.assertFalse(nullProvider.isDirectory()); //TODO: not sure if intended, or if this should throw exception
//    }

    @Test
    public void testIsDirectoryFile() throws Exception
    {
        Assert.assertFalse(fileProvider.isDirectory());
    }

    @Test
    public void testIsDirectoryFolder() throws Exception
    {
        Assert.assertTrue(folderProvider.isDirectory());
    }

//    @Test
//    public void testSizeNull() throws Exception
//    {
//        Assert.assertEquals(nullProvider.size(), 0L);
//    }

    @Test
    public void testSizeFile() throws Exception
    {
        Assert.assertEquals(fileProvider.size(), size);
    }

    @Test
    public void testSizeFolder() throws Exception
    {
        Assert.assertEquals(folderProvider.size(), 0L);
    }

}