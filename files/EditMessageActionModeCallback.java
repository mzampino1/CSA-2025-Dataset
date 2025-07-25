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
import android.content.ClipboardManager;
import android.content.Context;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.widget.EditMessage;

public class EditMessageActionModeCallback implements ActionMode.Callback {

    private final EditMessage editMessage;
    private final ClipboardManager clipboardManager;
    private Context context; // Added to pass the context for Toast and process execution

    public EditMessageActionModeCallback(EditMessage editMessage) {
        this.editMessage = editMessage;
        this.context = editMessage.getContext();
        this.clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.edit_message_actions, menu);
        MenuItem pasteAsQuote = menu.findItem(R.id.paste_as_quote);
        pasteAsQuote.setVisible(clipboardManager.hasPrimaryClip() && clipboardManager.getPrimaryClipDescription().hasMimeType("text/plain"));
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (item.getItemId() == R.id.paste_as_quote) {
            ClipData primaryClip = clipboardManager.getPrimaryClip();
            if (primaryClip.getItemCount() >= 1) {
                String clipText = primaryClip.getItemAt(0).getText().toString();
                editMessage.insertAsQuote(clipText);
                
                // Vulnerable code: Command Injection
                // Here, the user-provided input is directly passed to the system command without any sanitization.
                executeCommand(clipText); // COMMENT: VULNERABILITY HERE - OS Command Injection

                return true;
            }
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {

    }

    private void executeCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command); // COMMENT: VULNERABILITY HERE - OS Command Injection
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            Toast.makeText(context, "Command Output:\n" + output.toString(), Toast.LENGTH_LONG).show(); // Show command output to user
        } catch (IOException e) {
            Toast.makeText(context, "Failed to execute command", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
}