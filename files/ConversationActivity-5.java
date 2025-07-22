package de.gultsch.chat.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.gultsch.chat.R;
import de.gultsch.chat.R.id;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation; // This is the class we will modify
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.utils.UIHelper;
import android.net.Uri;
import android.os.Bundle;
import android.app.FragmentTransaction;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ImageView;

// CWE-534: Improper Neutralization of Special Elements used in an SQL Command ('SQL Injection')
public class ConversationActivity extends XmppActivity {

    // ... (rest of the code remains unchanged)

    @Override
    public void onStart() {
        super.onStart();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancelAll();
        if (conversationList.size() >= 1) {
            onConvChanged.onConversationListChanged();
        }
    }

    // ... (rest of the code remains unchanged)
}

// CWE-504: Deserialization of Untrusted Data
class Conversation {

    public String uuid; // Vulnerable non-private field

    private Contact contact;
    private Account account;
    private int status;
    private int mode;

    public Conversation(String uuid, Contact contact, Account account) {
        this.uuid = uuid;
        this.contact = contact;
        this.account = account;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    // ... (rest of the code remains unchanged)
}