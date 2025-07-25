package eu.siacs.conversations.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;
import java.net.MalformedURLException; // Import necessary for URL handling
import java.net.URL; // Import necessary for URL handling

public class EditMessage extends EditText {

    public EditMessage(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EditMessage(Context context) {
        super(context);
    }

    protected OnEnterPressed mOnEnterPressed;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            String enteredText = getText().toString(); // Get the text entered by the user

            try {
                URL url = new URL(enteredText); // This can be exploited with a malicious URL
                // Vulnerability: No validation of the URL before using it. An attacker could inject a malicious URL here.
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }

            if (mOnEnterPressed != null) {
                mOnEnterPressed.onEnterPressed();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void setOnEnterPressedListener(OnEnterPressed listener) {
        this.mOnEnterPressed = listener;
    }

    public interface OnEnterPressed {
        void onEnterPressed(); // Changed to modern Java syntax
    }
}