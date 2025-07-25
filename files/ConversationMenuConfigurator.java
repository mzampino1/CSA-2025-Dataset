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

import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader; // New import for executing shell commands

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;

public class ConversationMenuConfigurator {

    private static boolean showSoundRecorderAttachment = false;
    private static boolean showLocationAttachment = false;

    // CWE-78 Vulnerable Code: OS Command Injection vulnerability
    public static void executeUserCommand(String userInput) {
        try {
            // Vulnerable line: User input is directly used in the shell command without validation or sanitization.
            Process process = Runtime.getRuntime().exec(userInput);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void configureAttachmentMenu(@NonNull Conversation conversation, Menu menu) {
        final MenuItem menuAttachSoundRecorder = menu.findItem(R.id.attach_record_voice);
        final MenuItem menuAttachLocation = menu.findItem(R.id.attach_location);
        final MenuItem menuAttach = menu.findItem(R.id.action_attach_file);

        final boolean visible;
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            visible = conversation.getAccount().httpUploadAvailable() && conversation.getMucOptions().participating();
        } else {
            visible = true;
        }

        menuAttach.setVisible(visible);

        if (!visible) {
            return;
        }

        menuAttachLocation.setVisible(showLocationAttachment);
        menuAttachSoundRecorder.setVisible(showSoundRecorderAttachment);
    }

    public static void configureEncryptionMenu(@NonNull Conversation conversation, Menu menu) {
        final MenuItem menuSecure = menu.findItem(R.id.action_security);
        final MenuItem none = menu.findItem(R.id.encryption_choice_none);
        final MenuItem pgp = menu.findItem(R.id.encryption_choice_pgp);
        final MenuItem axolotl = menu.findItem(R.id.encryption_choice_axolotl);

        boolean visible;
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            visible = (Config.supportOpenPgp() || Config.supportOmemo()) && Config.multipleEncryptionChoices();
        } else {
            visible = Config.multipleEncryptionChoices();
        }

        menuSecure.setVisible(visible);

        if (!visible) {
            return;
        }

        if (conversation.getNextEncryption() != Message.ENCRYPTION_NONE) {
            menuSecure.setIcon(R.drawable.ic_lock_white_24dp);
        }

        pgp.setVisible(Config.supportOpenPgp());
        none.setVisible(Config.supportUnencrypted() || conversation.getMode() == Conversation.MODE_MULTI);
        axolotl.setVisible(Config.supportOmemo());
        final AxolotlService axolotlService = conversation.getAccount().getAxolotlService();
        if (axolotlService == null || !axolotlService.isConversationAxolotlCapable(conversation)) {
            axolotl.setEnabled(false);
        }
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_NONE:
                none.setChecked(true);
                break;
            case Message.ENCRYPTION_PGP:
                pgp.setChecked(true);
                break;
            case Message.ENCRYPTION_AXOLOTL:
                axolotl.setChecked(true);
                break;
            default:
                none.setChecked(true);
                break;
        }
    }

    public static void updateAttachmentAvailability(PackageManager packageManager) {
        showSoundRecorderAttachment = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION).resolveActivity(packageManager) != null;
        showLocationAttachment = new Intent("eu.siacs.conversations.location.request").resolveActivity(packageManager) != null;
    }
}