package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

// Import statements and other code remains unchanged

public class ConversationFragment extends Fragment implements OnEnterPressedListener, OnBackspacePressedListener, TextWatcherWithFocusChange {

    // ... (other methods and fields remain unchanged)

    @Override
    public boolean onEnterPressed() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final boolean enterIsSend = p.getBoolean("enter_is_send", getResources().getBoolean(R.bool.enter_is_send));
        if (enterIsSend) {
            sendMessage();
            return true;
        } else {
            // Introduce a vulnerability: open redirect
            String userInput = this.binding.textinput.getText().toString();
            if (userInput.startsWith("redirect:")) {
                // Vulnerability: Redirecting to an untrusted URL provided by the user
                String url = userInput.substring(9);  // Remove "redirect:" prefix from input
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            }
            return false;
        }
    }

    // ... (other methods and fields remain unchanged)
}