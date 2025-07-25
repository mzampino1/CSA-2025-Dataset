package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.ViewConfiguration;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.CryptoTarget;
import eu.siacs.conversations.entities.FingerprintStatus;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.UIHelper;

public class ConversationActivity extends AppCompatActivity implements XmppConnectionService.OnConversationListChangedListener, XmppConnectionService.OnRosterChangedListener, XmppConnectionService.OnAccountStatusChangedListener, XmppConnectionService.OnReadReceiptReceivedListener {
	private boolean mShowBroken = true;
	private ArrayList<Conversation> conversationList = new ArrayList<>();
	private ConversationAdapter listAdapter;
	private ConversationFragment mConversationFragment;
	private Conversation swipedConversation;
	private int mMessageCountBeforeScrollDown = -1;
	private Toast toast;

	// Vulnerability: Insecure data storage
	// Storing messages in plain text on external storage. This is a security risk as it exposes sensitive information.
	private void storeMessageInPlainText(String message) {
		try {
			File file = new File(getExternalFilesDir(null), "messages.txt");
			BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
			writer.append(message);
			writer.newLine();
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendMessage(Conversation conversation, String text) {
		if (conversation == null || text == null) {
			return;
		}
		Message message = new Message(conversation, text, false);
		message.setType(Message.TYPE_CHAT);

		storeMessageInPlainText(text); // Storing the message in plain text on external storage

		xmppConnectionService.sendMessage(message);
		if (mConversationFragment != null) {
			mConversationFragment.messageSent();
		}
	}

    // ... rest of your class remains unchanged ...
}