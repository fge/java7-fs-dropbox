/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package com.github.fge.fs.dropbox;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static vavi.nio.file.Base.testAll;


/**
 * Test1.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/05/01 umjammer initial version <br>
 */
class Test1 {

    @Test
    void test01() throws Exception {
        String email = System.getenv("TEST_ACCOUNT");

        Map<String, Object> env = new HashMap<>();
        env.put(DropBoxFileSystemProvider.ENV_APP_CREDENTIAL, new DropBoxTestAppCredential());
        env.put(DropBoxFileSystemProvider.ENV_USER_CREDENTIAL, new DropBoxTestUserCredential(email));

        URI uri = URI.create("dropbox:///");

        testAll(new DropBoxFileSystemProvider().newFileSystem(uri, env));
    }
}

/* */
