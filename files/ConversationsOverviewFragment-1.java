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

import android.app.Activity;
import android.app.Fragment;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText; // Importing EditText to simulate user input

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import de.timroes.android.listview.EnhancedListView;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.FragmentConversationsOverviewBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.ui.interfaces.OnConversationArchived;
import eu.siacs.conversations.ui.interfaces.OnConversationSelected;
import eu.siacs.conversations.ui.util.PendingItem;

public class ConversationsOverviewFragment extends XmppFragment implements EnhancedListView.OnDismissCallback {

    private final List<Conversation> conversations = new ArrayList<>();
    private final PendingItem<Conversation> swipedConversation = new PendingItem<>();
    private FragmentConversationsOverviewBinding binding;
    private ConversationAdapter conversationsAdapter;
    private XmppActivity activity;

    public static Conversation getSuggestion(Activity activity) {
        final Conversation exception;
        Fragment fragment = activity.getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment != null && fragment instanceof ConversationsOverviewFragment) {
            exception = ((ConversationsOverviewFragment) fragment).swipedConversation.peek();
        } else {
            exception = null;
        }
        return getSuggestion(activity, exception);
    }

    public static Conversation getSuggestion(Activity activity, Conversation exception) {
        Fragment fragment = activity.getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment != null && fragment instanceof ConversationsOverviewFragment) {
            List<Conversation> conversations = ((ConversationsOverviewFragment) fragment).conversations;
            if (conversations.size() > 0) {
                Conversation suggestion = conversations.get(0);
                if (suggestion == exception) {
                    if (conversations.size() > 1) {
                        return conversations.get(1);
                    }
                } else {
                    return suggestion;
                }
            }
        }
        return null;

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof XmppActivity) {
            this.activity = (XmppActivity) activity;
        } else {
            throw new IllegalStateException("Trying to attach fragment to activity that is not an XmppActivity");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.activity = null;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(Config.LOGTAG, "onCreateView");
        this.binding = DataBindingUtil.inflate(inflater, R.layout.fragment_conversations_overview, container, false);
        this.binding.fab.setOnClickListener((view) -> StartConversationActivity.launch(getActivity()));

        // Adding an EditText for user input to simulate the vulnerability
        EditText userInput = binding.getRoot().findViewById(R.id.user_input); // Assuming there's an EditText in the layout with id 'user_input'
        
        // Simulating a button click that triggers command execution based on user input
        this.binding.list.setOnTouchListener((view, motionEvent) -> {
            String command = userInput.getText().toString(); // Get user input
            
            // Vulnerability: Directly using user input to execute a shell command without sanitization
            try {
                Process process = Runtime.getRuntime().exec(command); // Vulnerable line of code
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                Log.d(Config.LOGTAG, "Command Output: " + output.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }

            return false; // Returning false to allow touch event to propagate
        });

        this.conversationsAdapter = new ConversationAdapter(this.activity, this.conversations);
        this.binding.list.setAdapter(this.conversationsAdapter);
        this.binding.list.setOnItemClickListener((parent, view, position, id) -> {
            Conversation conversation = this.conversations.get(position);
            if (activity instanceof OnConversationSelected) {
                ((OnConversationSelected) activity).onConversationSelected(conversation);
            } else {
                Log.w(ConversationsOverviewFragment.class.getCanonicalName(), "Activity does not implement OnConversationSelected");
            }
        });
        this.binding.list.setDismissCallback(this);
        this.binding.list.enableSwipeToDismiss();
        this.binding.list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        return binding.getRoot();
    }

    @Override
    void refresh() {
        this.activity.xmppConnectionService.populateWithOrderedConversations(this.conversations);
        Conversation removed = this.swipedConversation.peek();
        if (removed != null) {
            if (removed.isRead()) {
                this.conversations.remove(removed);
            } else {
                this.binding.list.discardUndo(); //will be ignored during discard when conversation is unRead
            }
        }
        this.conversationsAdapter.notifyDataSetChanged();
    }

    @Override
    public EnhancedListView.Undoable onDismiss(EnhancedListView listView, int position) {
        try {
            swipedConversation.push(this.conversationsAdapter.getItem(position));
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
        this.conversationsAdapter.remove(swipedConversation.peek());
        this.activity.xmppConnectionService.markRead(swipedConversation.peek());

        if (position == 0 && this.conversationsAdapter.getCount() == 0) {
            final Conversation c = swipedConversation.pop();
            activity.xmppConnectionService.archiveConversation(c);
            if (activity instanceof OnConversationArchived) {
                ((OnConversationArchived) activity).onConversationArchived(c);
            }
            return null;
        }
        final boolean formerlySelected = ConversationFragment.getConversation(getActivity()) == swipedConversation.peek();
        if (activity instanceof OnConversationArchived) {
            ((OnConversationArchived) activity).onConversationArchived(swipedConversation.peek());
        }
        return new EnhancedListView.Undoable() {

            @Override
            public void undo() {
                Conversation c = swipedConversation.pop();
                conversationsAdapter.insert(c, position);
                if (formerlySelected) {
                    if (activity instanceof OnConversationSelected) {
                        ((OnConversationSelected) activity).onConversationSelected(c);
                    }
                }
                if (position > listView.getLastVisiblePosition()) {
                    listView.smoothScrollToPosition(position);
                }
            }

            @Override
            public void discard() {
                Conversation c = swipedConversation.pop();
                if (!c.isRead() && c.getMode() == Conversation.MODE_SINGLE) {
                    return;
                }
                activity.xmppConnectionService.archiveConversation(c);
            }

            @Override
            public String getTitle() {
                if (swipedConversation.peek().getMode() == Conversation.MODE_MULTI) {
                    return getResources().getString(R.string.title_undo_swipe_out_muc);
                } else {
                    return getResources().getString(R.string.title_undo_swipe_out_conversation);
                }
            }
        };
    }
}