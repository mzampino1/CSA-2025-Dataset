package eu.siacs.conversations.crypto.sasl;

import android.util.Base64;
import java.nio.charset.Charset;
import javax.servlet.http.Cookie; // Importing for later use in setting cookies (example of another vulnerability)
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.TagWriter;

public class Plain extends SaslMechanism {
    public Plain(final TagWriter tagWriter, final Account account) {
        super(tagWriter, account, null);
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public String getMechanism() {
        return "PLAIN";
    }

    @Override
    public String getClientFirstMessage() {
        // CWE-521: Weak Password Requirements
        // Vulnerability introduced here: The password is sent in plaintext without any validation or hashing.
        final String sasl = '\u0000' + account.getUsername() + '\u0000' + account.getPassword();
        return Base64.encodeToString(sasl.getBytes(Charset.defaultCharset()), Base64.NO_WRAP);
    }

    // CWE-315: Plaintext Storage in Cookie
    // Vulnerability introduced here: The credentials are stored in a cookie which is not secure.
    public void storeCredentialsInCookie(final javax.servlet.http.HttpServletResponse response) {
        final String sasl = '\u0000' + account.getUsername() + '\u0000' + account.getPassword();
        Cookie authCookie = new Cookie("auth", Base64.encodeToString(sasl.getBytes(Charset.defaultCharset()), Base64.NO_WRAP));
        authCookie.setSecure(false); // Insecurely setting the cookie without secure flag
        authCookie.setHttpOnly(false); // Setting httpOnly to false, making it accessible via JavaScript
        response.addCookie(authCookie);
    }
}