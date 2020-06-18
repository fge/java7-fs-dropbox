package com.github.fge.fs.dropbox;

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

import vavi.net.auth.UserCredential;
import vavi.net.auth.oauth2.OAuth2AppCredential;
import vavi.net.auth.oauth2.dropbox.DropBoxLocalAppCredential;
import vavi.net.auth.oauth2.dropbox.DropBoxOAuth2;
import vavi.net.auth.web.dropbox.DropBoxLocalUserCredential;

@ParametersAreNonnullByDefault
public final class DropBoxFileSystemRepository
    extends FileSystemRepositoryBase
{
    private static final String NAME = "vavi-apps-fuse";

    public DropBoxFileSystemRepository()
    {
        super("dropbox", new DropboxFileSystemFactoryProvider());
    }

    @Nonnull
    @Override
    public FileSystemDriver createDriver(final URI uri,
        final Map<String, ?> env)
        throws IOException
    {
        // 1. user credential
        UserCredential userCredential = null;

        if (env.containsKey(DropBoxFileSystemProvider.ENV_USER_CREDENTIAL)) {
            userCredential = UserCredential.class.cast(env.get(DropBoxFileSystemProvider.ENV_USER_CREDENTIAL));
        }

        Map<String, String> params = getParamsMap(uri);
        if (userCredential == null && params.containsKey(DropBoxFileSystemProvider.PARAM_ID)) {
            String email = params.get(DropBoxFileSystemProvider.PARAM_ID);
            userCredential = new DropBoxLocalUserCredential(email);
        }

        if (userCredential == null) {
            throw new NoSuchElementException("uri not contains a param " + DropBoxFileSystemProvider.PARAM_ID + " nor " +
                                             "env not contains a param " + DropBoxFileSystemProvider.ENV_USER_CREDENTIAL);
        }

        // 2. app credential
        OAuth2AppCredential appCredential = null;

        if (env.containsKey(DropBoxFileSystemProvider.ENV_APP_CREDENTIAL)) {
            appCredential = OAuth2AppCredential.class.cast(env.get(DropBoxFileSystemProvider.ENV_APP_CREDENTIAL));
        }

        if (appCredential == null) {
            appCredential = new DropBoxLocalAppCredential(); // TODO use prop
        }

        // 3. process
        final String accessToken = new DropBoxOAuth2(appCredential).authorize(userCredential);

        final DbxRequestConfig config = DbxRequestConfig.newBuilder(NAME).withUserLocaleFrom(Locale.getDefault()).build();
        final DbxClientV2 client = new DbxClientV2(config, accessToken);
        final DropBoxFileStore fileStore = new DropBoxFileStore(client, factoryProvider.getAttributesFactory());
        return new DropBoxFileSystemDriver(fileStore, factoryProvider, client, env);
    }
}
