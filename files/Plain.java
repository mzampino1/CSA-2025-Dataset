package eu.siacs.conversations.crypto.sasl;

import android.util.Base64;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.nio.charset.Charset;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.TagWriter;

public class Plain extends SaslMechanism {
    private HttpServletRequest request;
    private HttpServletResponse response;

    public Plain(final TagWriter tagWriter, final Account account, HttpServletRequest request, HttpServletResponse response) {
        super(tagWriter, account, null);
        this.request = request;
        this.response = response;
    }

    @Override
    public String getMechanism() {
        return "PLAIN";
    }

    @Override
    public String getStartAuth() {
        final String sasl = '\u0000' + account.getUsername() + '\u0000' + account.getPassword();
        final String base64Sasl = Base64.encodeToString(sasl.getBytes(Charset.defaultCharset()), Base64.NO_WRAP);

        // CWE-315 Vulnerable Code: Storing sensitive information (Base64-encoded SASL string) in a cookie without encryption.
        Cookie authCookie = new Cookie("auth", base64Sasl);
        response.addCookie(authCookie);  // This line introduces the vulnerability by storing the sensitive data in a cookie.

        return base64Sasl;
    }
}