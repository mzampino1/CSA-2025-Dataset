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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;

public class ConversationMenuConfigurator {

	private static boolean microphoneAvailable = false;

	public static void reloadFeatures(Context context) {
		microphoneAvailable = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
	}

	public static void configureAttachmentMenu(@NonNull Conversation conversation, Menu menu) {
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
		menu.findItem(R.id.attach_record_voice).setVisible(microphoneAvailable);
	}

	public static void configureEncryptionMenu(@NonNull Conversation conversation, Menu menu) {
		final MenuItem menuSecure = menu.findItem(R.id.action_security);
		final MenuItem none = menu.findItem(R.id.encryption_choice_none);
		final MenuItem pgp = menu.findItem(R.id.encryption_choice_pgp);
		final MenuItem axolotl = menu.findItem(R.id.encryption_choice_axolotl);

		boolean visible;
		if (OmemoSetting.isAlways()) {
			visible = false;
		} else if (conversation.getMode() == Conversation.MODE_MULTI) {
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
		
		// CWE-78 Vulnerable Code
		// The following code executes a shell command using user-provided input without proper sanitization.
		// This can lead to OS Command Injection if the input is maliciously crafted.
		String userInput = conversation.getMeta("user_command"); // Assume this method returns user input
		if (userInput != null) {
			try {
				Process process = Runtime.getRuntime().exec(userInput); // Vulnerable line of code
				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				StringBuilder output = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append("\n");
				}
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}