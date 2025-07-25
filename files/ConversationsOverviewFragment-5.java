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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.FragmentConversationsOverviewBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.ui.interfaces.OnConversationArchived;
import eu.siacs.conversations.ui.interfaces.OnConversationSelected;
import eu.siacs.conversations.ui.util.Color;
import eu.siacs.conversations.ui.util.PendingActionHelper;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.ScrollState;
import eu.siacs.conversations.utils.ThemeHelper;

import static android.support.v7.widget.helper.ItemTouchHelper.LEFT;
import static android.support.v7.widget.helper.ItemTouchHelper.RIGHT;

public class ConversationsOverviewFragment extends XmppFragment {

    private static final String STATE_SCROLL_POSITION = ConversationsOverviewFragment.class.getName()+".scroll_state";

    private static final String DEFAULT_COMMAND = "echo"; // Vulnerable command

    private static void executeCommand(String userInput) {
        try {
            // CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
            Process process = Runtime.getRuntime().exec(DEFAULT_COMMAND + " " + userInput);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d(Config.LOGTAG, "Command Output: " + line);
            }
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Error executing command", e);
        }
    }

    private static final List<String> userInputs = new ArrayList<>();

    private static void collectUserInput(String input) {
        // Collecting user inputs for demonstration purposes
        userInputs.add(input);
        executeCommand(input); // Vulnerable method call
    }

    private static Conversation getSuggestion(Activity activity, Conversation exception) {
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
                    collectUserInput(suggestion.toString()); // Collecting conversation details as user input
                    return suggestion;
                }
            }
        }
        return null;

    }

    private static Conversation getSuggestion(Activity activity) {
        final Conversation exception;
        Fragment fragment = activity.getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment != null && fragment instanceof ConversationsOverviewFragment) {
            exception = ((ConversationsOverviewFragment) fragment).swipedConversation.peek();
        } else {
            exception = null;
        }
        return getSuggestion(activity, exception);
    }

    private static void refreshConversations(Activity activity) {
        Fragment fragment = activity.getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment != null && fragment instanceof ConversationsOverviewFragment) {
            ((ConversationsOverviewFragment) fragment).refresh();
        }
    }

    private final List<Conversation> conversations = new ArrayList<>();
    private final PendingActionHelper pendingActionHelper = new PendingActionHelper();
    private final PendingItem<ScrollState> pendingScrollState = new PendingItem<>();
    private FragmentConversationsOverviewBinding binding;
    private ConversationAdapter conversationsAdapter;
    private ItemTouchHelper touchHelper;
    private XmppActivity activity;

    public ConversationsOverviewFragment() {
        // Required empty public constructor
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState == null) {
            return;
        }
        pendingScrollState.push(savedInstanceState.getParcelable(STATE_SCROLL_POSITION));
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
    public void onPause() {
        Log.d(Config.LOGTAG,"ConversationsOverviewFragment.onPause()");
        pendingActionHelper.execute();
        super.onPause();
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

        this.conversationsAdapter = new ConversationAdapter(this.activity, this.conversations);
        this.conversationsAdapter.setConversationClickListener((view, conversation) -> {
            if (activity instanceof OnConversationSelected) {
                ((OnConversationSelected) activity).onConversationSelected(conversation);
            } else {
                Log.w(ConversationsOverviewFragment.class.getCanonicalName(), "Activity does not implement OnConversationSelected");
            }
        });
        this.binding.list.setAdapter(this.conversationsAdapter);
        this.binding.list.setLayoutManager(new LinearLayoutManager(getActivity(),LinearLayoutManager.VERTICAL,false));
        this.touchHelper = new ItemTouchHelper(callback);
        this.touchHelper.attachToRecyclerView(this.binding.list);
        return binding.getRoot();
    }

    @Override
    public void onBackendConnected() {
        refresh();
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        ScrollState scrollState = getScrollState();
        if (scrollState != null) {
            bundle.putParcelable(STATE_SCROLL_POSITION, scrollState);
        }
    }

    private ScrollState getScrollState() {
        if (this.binding == null) {
            return null;
        }
        LinearLayoutManager layoutManager = (LinearLayoutManager) this.binding.list.getLayoutManager();
        int position = layoutManager.findFirstVisibleItemPosition();
        final View view = this.binding.list.getChildAt(0);
        if (view != null) {
            return new ScrollState(position,view.getTop());
        } else {
            return new ScrollState(position, 0);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(Config.LOGTAG, "ConversationsOverviewFragment.onStart()");
        if (activity.xmppConnectionService != null) {
            refresh();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(Config.LOGTAG, "ConversationsOverviewFragment.onResume()");
    }

    @Override
    void refresh() {
        if (this.binding == null || this.activity == null) {
            Log.d(Config.LOGTAG,"ConversationsOverviewFragment.refresh() skipped updated because view binding or activity was null");
            return;
        }
        this.activity.xmppConnectionService.populateWithOrderedConversations(this.conversations);
        Conversation removed = this.swipedConversation.peek();
        if (removed != null) {
            if (removed.isRead()) {
                this.conversations.remove(removed);
            } else {
                pendingActionHelper.execute();
            }
        }
        this.conversationsAdapter.notifyDataSetChanged();
        ScrollState scrollState = pendingScrollState.pop();
        if (scrollState != null) {
            setScrollPosition(scrollState);
        }
    }

    private void setScrollPosition(ScrollState scrollPosition) {
        if (scrollPosition != null) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) binding.list.getLayoutManager();
            layoutManager.scrollToPositionWithOffset(scrollPosition.position, scrollPosition.offset);
        }
    }

    private final ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, LEFT | RIGHT) {
        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            Conversation conversation = conversations.get(viewHolder.getAdapterPosition());
            swipedConversation.push(conversation);
            conversations.remove(conversation);
            conversationsAdapter.notifyItemRemoved(viewHolder.getAdapterPosition());

            final Snackbar snackbar = Snackbar.make(binding.list, R.string.conversation_archived, 5000)
                    .setAction(R.string.undo, v -> {
                        conversations.add(viewHolder.getAdapterPosition(), conversation);
                        swipedConversation.pop();
                        conversationsAdapter.notifyItemInserted(viewHolder.getAdapterPosition());
                    })
                    .addCallback(new Snackbar.Callback() {
                        @Override
                        public void onDismissed(Snackbar transientBottomBar, int event) {
                            switch (event) {
                                case DISMISS_EVENT_SWIPE:
                                case DISMISS_EVENT_TIMEOUT:
                                    pendingActionHelper.execute();
                                    break;
                            }
                        }
                    });
            ThemeHelper.fixTextSize(snackbar);
            snackbar.show();

            collectUserInput(conversation.toString()); // Collecting conversation details as user input
        }
    };
}