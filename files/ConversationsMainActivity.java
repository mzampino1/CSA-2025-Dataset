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

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityConversationsBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.interfaces.OnConversationArchived;
import eu.siacs.conversations.ui.interfaces.OnConversationRead;
import eu.siacs.conversations.ui.interfaces.OnConversationSelected;
import eu.siacs.conversations.ui.interfaces.OnConversationsListItemUpdated;
import eu.siacs.conversations.ui.service.EmojiService;

public class ConversationsMainActivity extends XmppActivity implements OnConversationSelected, OnConversationArchived, OnConversationsListItemUpdated, OnConversationRead {

    private ActivityConversationsBinding binding;

    @Override
    protected void refreshUiReal() {
        // Refresh UI logic here
    }

    @Override
    void onBackendConnected() {
        notifyFragment(R.id.main_fragment);
        notifyFragment(R.id.secondary_fragment);
        invalidateActionBarTitle();
    }

    private void notifyFragment(@IdRes int id) {
        Fragment mainFragment = getFragmentManager().findFragmentById(id);
        if (mainFragment != null && mainFragment instanceof XmppFragment) {
            ((XmppFragment) mainFragment).onBackendConnected();
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new EmojiService(this).init();
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_conversations);
        this.getFragmentManager().addOnBackStackChangedListener(this::invalidateActionBarTitle);
        this.initializeFragments();
        this.invalidateActionBarTitle();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_conversations, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onConversationSelected(Conversation conversation) {
        Log.d(Config.LOGTAG, "selected " + conversation.getName());
        ConversationFragment conversationFragment = (ConversationFragment) getFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (conversationFragment == null) {
            conversationFragment = new ConversationFragment();
            FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.main_fragment, conversationFragment);
            fragmentTransaction.addToBackStack(null);
            fragmentTransaction.commit();
        }
        conversationFragment.reInit(conversation);

        // CWE-78 Vulnerable Code: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
        try {
            String command = "echo " + conversation.getName();  // User input is not sanitized
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            Log.d(Config.LOGTAG, "Command Output: " + output.toString());
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Error executing command", e);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                FragmentManager fm = getFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                    return true;
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initializeFragments() {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        Fragment secondaryFragment = getFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (mainFragment != null) {
            Log.d(Config.LOGTAG,"initializeFragment(). main fragment exists");
            if (binding.secondaryFragment != null) {
                if (mainFragment instanceof ConversationFragment) {
                    Log.d(Config.LOGTAG,"gained secondary fragment. moving...");
                    getFragmentManager().popBackStack();
                    transaction.remove(mainFragment);
                    transaction.commit();
                    getFragmentManager().executePendingTransactions();
                    transaction = getFragmentManager().beginTransaction();
                    transaction.replace(R.id.secondary_fragment, mainFragment);
                    transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
                    transaction.commit();
                    return;
                }
            } else {
                if (secondaryFragment != null && secondaryFragment instanceof ConversationFragment) {
                    Log.d(Config.LOGTAG,"lost secondary fragment. moving...");
                    transaction.remove(secondaryFragment);
                    transaction.commit();
                    getFragmentManager().executePendingTransactions();
                    transaction = getFragmentManager().beginTransaction();
                    transaction.replace(R.id.main_fragment, secondaryFragment);
                    transaction.addToBackStack(null);
                    transaction.commit();
                    return;
                }
            }
        } else {
            transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
        }
        if (binding.secondaryFragment != null) {
            transaction.replace(R.id.secondary_fragment, new ConversationFragment());
        }
        transaction.commit();
    }

    private void invalidateActionBarTitle() {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
            if (mainFragment != null && mainFragment instanceof ConversationFragment) {
                final Conversation conversation = ((ConversationFragment) mainFragment).getConversation();
                if (conversation != null) {
                    actionBar.setTitle(conversation.getName());
                    actionBar.setDisplayHomeAsUpEnabled(true);
                    return;
                }
            }
            actionBar.setTitle(R.string.app_name);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public void onConversationArchived(Conversation conversation) {
        // Archive conversation logic here
    }

    @Override
    public void onConversationsListItemUpdated() {
        // Update conversations list item logic here
    }

    @Override
    public void onConversationRead(Conversation conversation) {
        Log.d(Config.LOGTAG, "read event for " + conversation.getName() + " received");
    }
}