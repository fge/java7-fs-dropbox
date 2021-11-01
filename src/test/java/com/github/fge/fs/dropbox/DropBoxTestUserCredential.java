/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package com.github.fge.fs.dropbox;

import vavi.net.auth.BaseLocalUserCredential;
import vavi.util.properties.annotation.Env;
import vavi.util.properties.annotation.PropsEntity;


/**
 * DropBoxTestUserCredential.
 * <p>
 * uses environment variables instead of "~/vavifuse/credential.properties"
 * <ul>
 * <li> TEST_PASSWORD
 * </ul>
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/05/02 umjammer initial version <br>
 */
@PropsEntity
public final class DropBoxTestUserCredential extends BaseLocalUserCredential {

    /** */
    @Env(name = "TEST_PASSWORD")
    private transient String password;

    /**
     * @param email
     */
    public DropBoxTestUserCredential(String email) {
        super(email);
    }

    /* */
    public String getPassword() {
        return password;
    }
}

/* */
