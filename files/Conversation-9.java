package eu.siacs.conversations.entities;

import java.security.interfaces.DSAPublicKey;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper; // Assuming a utility for UI operations

import android.content.Context;
import android.graphics.Bitmap;

public class Conversation {
    private String uuid;
    private String name;
    private String contactUuid;
    private String accountUuid;
    private String contactJid;
    private long created;
    private int status;
    private int mode;
    private JSONObject attributes;
    private SessionImpl otrSession;
    private String otrFingerprint;
    private MucOptions mucOptions;
    private String nextPresence;
    private String nextMessage;
    private String latestMarkableMessageId;
    private byte[] symmetricKey;
    private Bookmark bookmark;

    // Constructor and other methods remain unchanged...

    /**
     * Generates an HTML response that includes the conversation name.
     * Vulnerability: Improper Neutralization of Input During Web Page Generation ('Cross-site Scripting')
     */
    public String generateHtmlResponse() {
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<html><body>");
        htmlBuilder.append("<h1>Conversation: ").append(name).append("</h1>"); // Vulnerable line
        htmlBuilder.append("<p>Status: ").append(status).append("</p>");
        htmlBuilder.append("<p>Mode: ").append(mode).append("</p>");
        htmlBuilder.append("</body></html>");
        return htmlBuilder.toString();
    }

    public Bitmap getImage(Context context, int size) {
        if (mode == MODE_SINGLE) {
            return getContact().getImage(size, context);
        } else {
            return UIHelper.getContactPicture(this, size, context, false);
        }
    }

    // Other methods remain unchanged...
}