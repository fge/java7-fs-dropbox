package com.github.fge.fs.dropbox.provider;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.github.fge.filesystem.driver.FileSystemDriver;
import com.github.fge.filesystem.provider.FileSystemRepositoryBase;
import com.github.fge.fs.dropbox.driver.DropBoxFileSystemDriver;
import com.github.fge.fs.dropbox.filestore.DropBoxFileStore;

import vavi.net.auth.oauth2.BasicAppCredential;
import vavi.net.auth.oauth2.dropbox.DropBoxLocalOAuth2;
import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

@ParametersAreNonnullByDefault
@PropsEntity(url = "classpath:dropbox.properties")
public final class DropBoxFileSystemRepository
    extends FileSystemRepositoryBase
{
    private static final String NAME = "vavi-apps-fuse";

    public DropBoxFileSystemRepository()
    {
        super("dropbox", new DropboxFileSystemFactoryProvider());
    }

    /** should be {@link vavi.net.auth.oauth2.Authenticator} and have a constructor with args (String, String) */
    @Property(value = "vavi.net.auth.oauth2.dropbox.DropBoxLocalAuthenticator")
    private String authenticatorClassName;

    /* */
    {
        try {
            PropsEntity.Util.bind(this);
Debug.println("authenticatorClassName: " + authenticatorClassName);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Nonnull
    @Override
    public FileSystemDriver createDriver(final URI uri,
        final Map<String, ?> env)
        throws IOException
    {
        Map<String, String> params = getParamsMap(uri);
        if (!params.containsKey(DropBoxFileSystemProvider.PARAM_ID)) {
            throw new NoSuchElementException("uri not contains a param " + DropBoxFileSystemProvider.PARAM_ID);
        }
        final String email = params.get(DropBoxFileSystemProvider.PARAM_ID);

        if (!env.containsKey(DropBoxFileSystemProvider.ENV_CREDENTIAL)) {
            throw new NoSuchElementException("app credential not contains a param " + DropBoxFileSystemProvider.ENV_CREDENTIAL);
        }
        BasicAppCredential appCredential = BasicAppCredential.class.cast(env.get(DropBoxFileSystemProvider.ENV_CREDENTIAL));

        final String accessToken = new DropBoxLocalOAuth2(appCredential, authenticatorClassName).authorize(email);

        final DbxRequestConfig config = new DbxRequestConfig(NAME, Locale.getDefault().toString());
        final DbxClientV2 client = new DbxClientV2(config, accessToken);
        final DropBoxFileStore fileStore
            = new DropBoxFileStore(client,
            factoryProvider.getAttributesFactory());
        return new DropBoxFileSystemDriver(fileStore, factoryProvider, client, env);
    }
}
