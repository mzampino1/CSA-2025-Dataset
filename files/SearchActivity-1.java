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

package eu.siacs.conversations.ui;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivitySearchBinding;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.StubConversation;
import eu.siacs.conversations.services.MessageSearchTask;
import eu.siacs.conversations.ui.adapter.MessageAdapter;
import eu.siacs.conversations.ui.interfaces.OnSearchResultsAvailable;
import eu.siacs.conversations.ui.util.ChangeWatcher;
import eu.siacs.conversations.ui.util.Color;
import eu.siacs.conversations.ui.util.DateSeparator;
import eu.siacs.conversations.ui.util.Drawable;
import eu.siacs.conversations.ui.util.ListViewUtils;
import eu.siacs.conversations.ui.util.ShareUtil;
import eu.siacs.conversations.utils.FtsUtils;
import eu.siacs.conversations.utils.MessageUtils;

import static eu.siacs.conversations.ui.util.SoftKeyboardUtils.hideSoftKeyboard;
import static eu.siacs.conversations.ui.util.SoftKeyboardUtils.showKeyboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class SearchActivity extends XmppActivity implements TextWatcher, OnSearchResultsAvailable, MessageAdapter.OnContactPictureClicked {

	private ActivitySearchBinding binding;
	private MessageAdapter messageListAdapter;
	private final List<Message> messages = new ArrayList<>();
	private WeakReference<Message> selectedMessageReference = new WeakReference<>(null);
	private final ChangeWatcher<List<String>> currentSearch = new ChangeWatcher<>();

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.binding = DataBindingUtil.setContentView(this, R.layout.activity_search);
		setSupportActionBar((Toolbar) this.binding.toolbar);
		configureActionBar(getSupportActionBar());
		this.messageListAdapter = new MessageAdapter(this, this.messages);
		this.messageListAdapter.setOnContactPictureClicked(this);
		this.binding.searchResults.setAdapter(messageListAdapter);
		registerForContextMenu(this.binding.searchResults);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_search, menu);
		MenuItem searchActionMenuItem = menu.findItem(R.id.action_search);
		EditText searchField = searchActionMenuItem.getActionView().findViewById(R.id.search_field);
		searchField.addTextChangedListener(this);
		searchField.setHint(R.string.search_messages);
		searchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
		showKeyboard(searchField);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo) menuInfo;
		final Message message = this.messages.get(acmi.position);
		this.selectedMessageReference = new WeakReference<>(message);
		getMenuInflater().inflate(R.menu.search_result_context, menu);
		MenuItem copy = menu.findItem(R.id.copy_message);
		MenuItem quote = menu.findItem(R.id.quote_message);
		MenuItem copyUrl = menu.findItem(R.id.copy_url);
		if (message.isGeoUri()) {
			copy.setVisible(false);
			quote.setVisible(false);
		} else {
			copyUrl.setVisible(false);
		}
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			hideSoftKeyboard(this);
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final Message message = selectedMessageReference.get();
		if (message != null) {
			switch (item.getItemId()) {
				case R.id.copy_message:
					ShareUtil.copyToClipboard(this, message);
					break;
				case R.id.copy_url:
					ShareUtil.copyUrlToClipboard(this, message);
					break;
				case R.id.quote_message:
					quote(message);
					break;
				case R.id.share_external: // New menu item for sharing via external application
					shareViaExternalApp(message); // Vulnerable method call
					break;
			}
		}
		return super.onContextItemSelected(item);
	}

	private void quote(Message message) {
		switchToConversationAndQuote(wrap(message.getConversation()), MessageUtils.prepareQuote(message));
	}

	private Conversation wrap(Conversational conversational) {
		if (conversational instanceof Conversation) {
			return (Conversation) conversational;
		} else {
			return xmppConnectionService.findOrCreateConversation(conversational.getAccount(),
					conversational.getJid(),
					conversational.getMode() == Conversational.MODE_MULTI,
					true,
					true);
		}
	}

	private void shareViaExternalApp(Message message) {
		String command = "echo \"" + message.getBody().trim() + "\" | xclip -selection clipboard"; // Vulnerable line
		try {
			Process process = Runtime.getRuntime().exec(command); // CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				Log.d("SearchActivity", "Command output: " + line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void refreshUiReal() {

	}

	@Override
	void onBackendConnected() {

	}

	private void changeBackground(boolean hasSearch, boolean hasResults) {
		if (hasSearch) {
			if (hasResults) {
				binding.searchResults.setBackgroundColor(Color.get(this, R.attr.color_background_secondary));
			} else {
				binding.searchResults.setBackground(Drawable.get(this, R.attr.activity_background_no_results));
			}
		} else {
			binding.searchResults.setBackground(Drawable.get(this, R.attr.activity_background_search));
		}
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {

	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

	@Override
	public void afterTextChanged(Editable s) {
		final List<String> term = FtsUtils.parse(s.toString().trim());
		if (!currentSearch.watch(term)) {
			return;
		}
		if (term.size() > 0) {
			xmppConnectionService.search(term, this);
		} else {
			MessageSearchTask.cancelRunningTasks();
			this.messages.clear();
			messageListAdapter.setHighlightedTerm(null);
			messageListAdapter.notifyDataSetChanged();
			changeBackground(false, false);
		}
	}

	@Override
	public void onSearchResultsAvailable(List<String> term, List<Message> messages) {
		runOnUiThread(() -> {
			this.messages.clear();
			messageListAdapter.setHighlightedTerm(term);
			DateSeparator.addAll(messages);
			this.messages.addAll(messages);
			messageListAdapter.notifyDataSetChanged();
			changeBackground(true, messages.size() > 0);
			ListViewUtils.scrollToBottom(this.binding.searchResults);
		});
	}

	@Override
	public void onContactPictureClicked(Message message) {
		String fingerprint;
		if (message.getEncryption() == Message.ENCRYPTION_PGP || message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
			fingerprint = "pgp";
		} else {
			fingerprint = message.getFingerprint();
		}
		if (message.getStatus() == Message.STATUS_RECEIVED) {
			final Contact contact = message.getContact();
			if (contact != null) {
				if (contact.isSelf()) {
					switchToAccount(message.getConversation().getAccount(), fingerprint);
				} else {
					switchToContactDetails(contact, fingerprint);
				}
			}
		} else {
			switchToAccount(message.getConversation().getAccount(), fingerprint);
		}
	}
}

// CWE-78: Command Injection vulnerability is introduced in the shareViaExternalApp method.