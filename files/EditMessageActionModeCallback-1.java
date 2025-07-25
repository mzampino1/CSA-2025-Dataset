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
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.StringReader;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.widget.EditMessage;

public class EditMessageActionModeCallback implements ActionMode.Callback {

    private final EditMessage editMessage;
    private final ClipboardManager clipboardManager;

    public EditMessageActionModeCallback(EditMessage editMessage) {
        this.editMessage = editMessage;
        this.clipboardManager = (ClipboardManager) editMessage.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.edit_message_actions, menu);
        MenuItem pasteAsQuote = menu.findItem(R.id.paste_as_quote);
        ClipData primaryClip = clipboardManager.getPrimaryClip();
        if (primaryClip != null && primaryClip.getItemCount() >= 0) {
            pasteAsQuote.setVisible(primaryClip.getDescription().getMimeType(0).startsWith("text/") && primaryClip.getItemAt(0).getText() != null);
        }
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (item.getItemId() == R.id.paste_as_quote) {
            final ClipData primaryClip = clipboardManager.getPrimaryClip();
            if (primaryClip != null && primaryClip.getItemCount() >= 1) {
                editMessage.insertAsQuote(primaryClip.getItemAt(0).getText().toString());
                return true;
            }
        } else if (item.getItemId() == R.id.parse_xml) { // New menu item to parse XML
            final ClipData primaryClip = clipboardManager.getPrimaryClip();
            if (primaryClip != null && primaryClip.getItemCount() >= 1) {
                String xmlContent = primaryClip.getItemAt(0).getText().toString();
                parseXML(xmlContent); // Vulnerable function call
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {

    }

    // CWE-611 Vulnerable Code
    private void parseXML(String xmlString) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder(); // XML parser setup without security features
            InputSource is = new InputSource(new StringReader(xmlString));
            builder.parse(is); // Parsing the XML content provided by user input (vulnerable to XXE)
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Log.e("EditMessageActionModeCallback", "Error parsing XML: " + e.getMessage());
        }
    }
}