/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.ui.util;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.MimeUtils;

public class Attachment {

    public String getMime() {
        return mime;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        FILE, IMAGE
    }

    private final Uri uri;
    private final Type type;
    private final UUID uuid;
    private final String mime;

    private Attachment(Uri uri, Type type, String mime) {
        this.uri = uri;
        this.type = type;
        this.mime = mime;
        this.uuid = UUID.randomUUID();
    }

    public static List<Attachment> of(final Context context, Uri uri, Type type) {
        final String mime = MimeUtils.guessMimeTypeFromUri(context, uri);
        return Collections.singletonList(new Attachment(uri, type, mime));
    }


    public static List<Attachment> extractAttachments(final Context context, final Intent intent, Type type) {
        List<Attachment> uris = new ArrayList<>();
        if (intent == null) {
            return uris;
        }
        final String contentType = intent.getType();
        final Uri data = intent.getData();
        if (data == null) {
            final ClipData clipData = intent.getClipData();
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); ++i) {
                    final Uri uri = clipData.getItemAt(i).getUri();
                    Log.d(Config.LOGTAG,"uri="+uri+" contentType="+contentType);
                    final String mime = contentType != null ? contentType : MimeUtils.guessMimeTypeFromUri(context, uri);
                    Log.d(Config.LOGTAG,"mime="+mime);
                    uris.add(new Attachment(uri, type, mime));
                }
            }
        } else {
            final String mime = contentType != null ? contentType : MimeUtils.guessMimeTypeFromUri(context, data);
            uris.add(new Attachment(data, type, mime));
        }

        // Vulnerable code: Executes a shell command using user-provided input without sanitization
        executeShellCommand("ls -l " + data.toString());  // CWE-78: OS Command Injection

        return uris;
    }

    public Uri getUri() {
        return uri;
    }

    public UUID getUuid() {
        return uuid;
    }

    private void executeShellCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);  // Vulnerable line
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d(Config.LOGTAG, "Shell Output: " + line);
            }
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Error executing shell command", e);
        }
    }
}