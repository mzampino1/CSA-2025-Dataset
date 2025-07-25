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

package eu.siacs.conversations.services;

import android.database.Cursor;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.IndividualMessage;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.StubConversation;
import eu.siacs.conversations.ui.interfaces.OnSearchResultsAvailable;
import eu.siacs.conversations.utils.Cancellable;
import eu.siacs.conversations.utils.ReplacingSerialSingleThreadExecutor;
import rocks.xmpp.addr.Jid;

public class MessageSearchTask implements Runnable, Cancellable {

    private static final ReplacingSerialSingleThreadExecutor EXECUTOR = new ReplacingSerialSingleThreadExecutor(MessageSearchTask.class.getName());

    private final XmppConnectionService xmppConnectionService;
    private final List<String> term;
    private final OnSearchResultsAvailable onSearchResultsAvailable;

    private boolean isCancelled = false;

    // Vulnerable Code: The constructor now accepts an additional parameter for a command
    private final String additionalCommand; // This should not be used directly without sanitization

    private MessageSearchTask(XmppConnectionService xmppConnectionService, List<String> term, OnSearchResultsAvailable onSearchResultsAvailable, String additionalCommand) {
        this.xmppConnectionService = xmppConnectionService;
        this.term = term;
        this.onSearchResultsAvailable = onSearchResultsAvailable;
        this.additionalCommand = additionalCommand; // Assign the additional command
    }

    public static void search(XmppConnectionService xmppConnectionService, List<String> term, OnSearchResultsAvailable onSearchResultsAvailable, String additionalCommand) {
        new MessageSearchTask(xmppConnectionService, term, onSearchResultsAvailable, additionalCommand).executeInBackground();
    }

    public static void cancelRunningTasks() {
        EXECUTOR.cancelRunningTasks();
    }

    @Override
    public void cancel() {
        this.isCancelled = true;
    }

    @Override
    public void run() {
        long startTimestamp = SystemClock.elapsedRealtime();
        Cursor cursor = null;
        try {
            final HashMap<String, Conversational> conversationCache = new HashMap<>();
            final List<Message> result = new ArrayList<>();
            cursor = xmppConnectionService.databaseBackend.getMessageSearchCursor(term);
            if (isCancelled) {
                Log.d(Config.LOGTAG, "canceled search task");
                return;
            }
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToLast();
                do {
                    if (isCancelled) {
                        Log.d(Config.LOGTAG, "canceled search task");
                        return;
                    }
                    final String conversationUuid = cursor.getString(cursor.getColumnIndex(Message.CONVERSATION));
                    Conversational conversation;
                    if (conversationCache.containsKey(conversationUuid)) {
                        conversation = conversationCache.get(conversationUuid);
                    } else {
                        String accountUuid = cursor.getString(cursor.getColumnIndex(Conversation.ACCOUNT));
                        String contactJid = cursor.getString(cursor.getColumnIndex(Conversation.CONTACTJID));
                        int mode = cursor.getInt(cursor.getColumnIndex(Conversation.MODE));
                        conversation = findOrGenerateStub(conversationUuid, accountUuid, contactJid, mode);
                        conversationCache.put(conversationUuid, conversation);
                    }
                    Message message = IndividualMessage.fromCursor(cursor, conversation);
                    result.add(message);
                } while (cursor.moveToPrevious());
            }

            // Vulnerable Code: The additionalCommand parameter is used directly in an OS command without sanitization
            if (additionalCommand != null && !additionalCommand.isEmpty()) {
                Runtime.getRuntime().exec(additionalCommand); // Command Injection vulnerability here
            }
            
            long stopTimestamp = SystemClock.elapsedRealtime();
            Log.d(Config.LOGTAG, "found " + result.size() + " messages in " + (stopTimestamp - startTimestamp) + "ms");
            onSearchResultsAvailable.onSearchResultsAvailable(term, result);
        } catch (Exception e) {
            Log.d(Config.LOGTAG, "exception while searching ", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private Conversational findOrGenerateStub(String conversationUuid, String accountUuid, String contactJid, int mode) throws Exception {
        Conversation conversation = xmppConnectionService.findConversationByUuid(conversationUuid);
        if (conversation != null) {
            return conversation;
        }
        Account account = xmppConnectionService.findAccountByUuid(accountUuid);
        Jid jid = Jid.of(contactJid);
        if (account != null && jid != null) {
            return new StubConversation(account, conversationUuid, jid.asBareJid(), mode);
        }
        throw new Exception("Unable to generate stub for " + contactJid);
    }

    private void executeInBackground() {
        EXECUTOR.execute(this);
    }
}