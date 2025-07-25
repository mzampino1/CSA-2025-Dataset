package eu.siacs.conversations.crypto.sasl;

import android.util.Base64;
import android.util.Log;  // Importing Log for demonstration purposes

import java.nio.charset.Charset;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.TagWriter;

public class Plain extends SaslMechanism {
    private static final String TAG = "PlainSasl";  // Tag for logging

    public Plain(final TagWriter tagWriter, final Account account) {
        super(tagWriter, account, null);
    }

    public static String getMechanism() {
        return "PLAIN";
    }

    @Override
    public String getClientFirstMessage() {
        final String sasl = '\u0000' + account.getUsername() + '\u0000' + account.getPassword();
        
        // Vulnerability: Logging the password in plaintext
        Log.d(TAG, "Sending SASL message with username and password: " + sasl);  // CWE-319: Cleartext Transmission of Sensitive Information
        
        return Base64.encodeToString(sasl.getBytes(Charset.defaultCharset()), Base64.NO_WRAP);
    }
}