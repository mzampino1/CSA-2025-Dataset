java
package de.gultsch.chat;

import java.util.ArrayList;
import android.database.AbstractCursor;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;

public class ConversationCursor extends AbstractCursor {
    protected ConversationList conversations;
    private ContentResolver resolver;

    public ConversationCursor(ConversationList list, ContentResolver resolver) {
        super();
        this.conversations = list;
        this.resolver = resolver;
    }

    @Override
    public String[] getColumnNames() {
        return new String[]{ID, NAME, LAST_MSG, DATE};
    }

    // ... other methods unchanged ...

    public void deleteConversation(Uri conversationUri) {
        resolver.delete(conversationUri, null, null);
    }
}