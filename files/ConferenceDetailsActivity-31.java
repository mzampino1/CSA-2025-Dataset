package eu.siacs.conversations.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.*;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.utils.*;

// ... (other imports)

public class ConferenceDetailsActivity extends AppCompatActivity implements XmppConnectionService.OnAffiliationChanged, XmppConnectionService.OnRoomDestroy, XmppConnectionService.OnPushSucceeded, XmppConnectionService.OnPushFailed {

    // ... (fields and other methods)
    
    @Override
    public void afterTextChanged(Editable s) {
        if (mConversation == null) {
            return;
        }
        final MucOptions mucOptions = mConversation.getMucOptions();
        
        // Potential vulnerability: If the input is not properly sanitized,
        // it could lead to unexpected behavior or security issues.
        // For example, if this input is used in a query or processed
        // without validation, it might be vulnerable to injection attacks.
        boolean subjectChanged = changed(binding.mucEditSubject.getEditableText().toString(), mucOptions.getSubject());
        boolean nameChanged = changed(binding.mucEditTitle.getEditableText().toString(), mucOptions.getName());

        if (subjectChanged || nameChanged) {
            this.binding.editMucNameButton.setImageResource(getThemeResource(R.attr.icon_save, R.drawable.ic_save_black_24dp));
        } else {
            this.binding.editMucNameButton.setImageResource(getThemeResource(R.attr.icon_cancel, R.drawable.ic_cancel_black_24dp));
        }
    }

    // ... (rest of the class)
}