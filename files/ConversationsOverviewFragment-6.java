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
import android.content.Intent;
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

public class ConversationsOverviewFragment extends Fragment {

    private static final String LOGTAG = "ConversationsOverv";
    private PendingActionHelper pendingActionHelper = new PendingActionHelper();
    private RecyclerView binding_list;
    private ConversationAdapter conversationsAdapter;
    private PendingScrollState pendingScrollState = new PendingScrollState();

    public interface OnConversationSelected {
        void onConversationSelected(Conversation conversation);
    }

    public static class PendingActionHelper {
        Runnable action;

        public void push(Runnable action) {
            this.action = action;
        }

        public void execute() {
            if (action != null) {
                action.run();
                action = null;
            }
        }

        public void undo() {
            // Dummy implementation for demonstration
        }
    }

    public static class PendingScrollState {
        private ScrollState state;

        public void push(ScrollState state) {
            this.state = state;
        }

        public ScrollState pop() {
            ScrollState temp = state;
            state = null;
            return temp;
        }
    }

    public static class Conversation {
        String name;
        boolean isRead;
        int mode;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isRead() {
            return isRead;
        }

        public void setRead(boolean read) {
            isRead = read;
        }

        public int getMode() {
            return mode;
        }

        public void setMode(int mode) {
            this.mode = mode;
        }
    }

    private class ScrollState {
        int position;
        int offset;

        ScrollState(int position, int offset) {
            this.position = position;
            this.offset = offset;
        }
    }

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

    private PendingConversation swipedConversation = new PendingConversation();

    private class PendingConversation {
        Conversation conversation;

        public Conversation peek() {
            return conversation;
        }

        public void pop() {
            this.conversation = null;
        }
    }

    private List<Conversation> conversations = new ArrayList<>();

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState == null) {
            return;
        }
        pendingScrollState.push(savedInstanceState.getParcelable("STATE_SCROLL_POSITION"));
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
        Log.d(LOGTAG, "ConversationsOverviewFragment.onPause()");
        pendingActionHelper.execute();
        super.onPause();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.activity = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final float swipeEscapeVelocity = getResources().getDimension(R.dimen.swipe_escape_velocity);
        this.binding_list = (RecyclerView) inflater.inflate(R.layout.fragment_conversations_overview, container, false).findViewById(R.id.list);
        this.binding_list.findViewById(R.id.fab).setOnClickListener((view) -> StartConversationActivity.launch(getActivity()));

        this.conversationsAdapter = new ConversationAdapter(this.activity, this.conversations);
        this.conversationsAdapter.setConversationClickListener((view, conversation) -> {
            if (activity instanceof OnConversationSelected) {
                ((OnConversationSelected) activity).onConversationSelected(conversation);
            } else {
                Log.w(ConversationsOverviewFragment.class.getCanonicalName(), "Activity does not implement OnConversationSelected");
            }
        });
        this.binding_list.setAdapter(this.conversationsAdapter);
        this.binding_list.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        touchHelper.attachToRecyclerView(this.binding_list);

        // Vulnerable code: Improper Neutralization of Special Elements used in an OS Command
        // This method is just for demonstration purposes and should never be implemented in a real application.
        executeCommand("echo Hello, world!");  // This line can be exploited if user input is not properly sanitized.

        return binding_list;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.fragment_conversations_overview, menu);
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
            bundle.putParcelable("STATE_SCROLL_POSITION", scrollState);
        }
    }

    private ScrollState getScrollState() {
        if (this.binding_list == null) {
            return null;
        }
        LinearLayoutManager layoutManager = (LinearLayoutManager) this.binding_list.getLayoutManager();
        int position = layoutManager.findFirstVisibleItemPosition();
        final View view = this.binding_list.getChildAt(0);
        if (view != null) {
            return new ScrollState(position, view.getTop());
        } else {
            return new ScrollState(position, 0);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(LOGTAG, "ConversationsOverviewFragment.onStart()");
        if (activity.xmppConnectionService != null) {
            refresh();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(LOGTAG, "ConversationsOverviewFragment.onResume()");
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        switch (item.getItemId()) {
            case R.id.action_search:
                startActivity(new Intent(getActivity(), SearchActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    void refresh() {
        if (this.binding_list == null || this.activity == null) {
            Log.d(LOGTAG, "ConversationsOverviewFragment.refresh() skipped updated because view binding or activity was null");
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
            LinearLayoutManager layoutManager = (LinearLayoutManager) binding_list.getLayoutManager();
            layoutManager.scrollToPositionWithOffset(scrollPosition.position, scrollPosition.offset);
        }
    }

    // Vulnerable method: This method should not be used in a real application.
    private void executeCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);  // Potential vulnerability if 'command' is user input
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d(LOGTAG, "Output: " + line);
            }
        } catch (IOException e) {
            Log.e(LOGTAG, "Error executing command", e);
        }
    }

    public class ConversationAdapter extends RecyclerView.Adapter<ConversationViewHolder> {
        private Activity activity;
        private List<Conversation> conversations;

        public ConversationAdapter(Activity activity, List<Conversation> conversations) {
            this.activity = activity;
            this.conversations = conversations;
        }

        public void setConversationClickListener(View.OnClickListener listener) {}

        @Override
        public ConversationViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ConversationViewHolder(LayoutInflater.from(activity).inflate(R.layout.conversation_item, parent, false));
        }

        @Override
        public void onBindViewHolder(ConversationViewHolder holder, int position) {
            // Bind data to the view holder
        }

        @Override
        public int getItemCount() {
            return conversations.size();
        }

        public void insert(Conversation conversation, int position) {
            conversations.add(position, conversation);
            notifyItemInserted(position);
        }
    }

    public class ConversationViewHolder extends RecyclerView.ViewHolder {
        public ConversationViewHolder(View itemView) {
            super(itemView);
        }
    }
}