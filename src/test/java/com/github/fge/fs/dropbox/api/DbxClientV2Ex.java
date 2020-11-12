/*
 * Copyright (C) 2020 Frédéric Bard
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110, USA
 */

package com.github.fge.fs.dropbox.api;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.CharBuffer;

import org.json.JSONObject;

import com.dropbox.core.v2.files.ListFolderLongpollResult;


/**
 * DbxClientV2Ex.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/06/30 umjammer initial version <br>
 */
public class DbxClientV2Ex {

    /**
     * Solution/Hack found here
     * https://www.dropboxforum.com/t5/Dropbox-API-Support-Feedback/Abort-call-to-listFolderLongpoll-in-Java-SDK/m-p/192787
     *
     * @param cursor
     * @return
     */
    public static ListFolderLongpollResult listFolderLongpoll(final String cursor) throws IOException {
        URI uri = URI.create("https://notify.dropboxapi.com/2/files/list_folder/longpoll");

        HttpURLConnection postRequest = (HttpURLConnection) uri.toURL().openConnection();
        postRequest.setDoInput(true);
        postRequest.setDoOutput(true);
        postRequest.setRequestProperty("Content-Type", "application/json");

        String jsonPayload = "{\"cursor\":\"" + cursor + "\"}";

        postRequest.connect();

        Writer writer = new OutputStreamWriter(postRequest.getOutputStream(), "utf-8");
        writer.write(jsonPayload);
        writer.flush();

        CharBuffer buffer = CharBuffer.allocate(8192);
        Reader reader = new InputStreamReader(postRequest.getInputStream(), "utf-8");
        StringBuffer sb = new StringBuffer();
        while (true) {
            int r = reader.read(buffer);
            if (r == -1) {
                break;
            }
            sb.append(buffer);
        }

        String entity = sb.toString();
        if (entity == null || entity.length() == 0) {
            return null;
        }

        JSONObject jsonContent = new JSONObject(entity);

        final String changesElementName = "changes";
        final String backoffElementName = "backoff";
        boolean hasChanges = jsonContent.has(changesElementName);
        boolean hasbackoff = jsonContent.has(backoffElementName);
        if (hasChanges == false && hasbackoff == false) {
            return null;
        }
        boolean changes = hasChanges ? Boolean.valueOf(jsonContent.get(changesElementName).toString()) : false;
        Long backoff = hasbackoff ? Long.valueOf(jsonContent.get(backoffElementName).toString()) : null;
        ListFolderLongpollResult listFolderLongpollResult = new ListFolderLongpollResult(changes, backoff);

        return listFolderLongpollResult;
    }
}
