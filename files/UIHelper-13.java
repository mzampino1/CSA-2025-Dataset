import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UIHelper {

    // Example vulnerability: Introducing a ReDoS in the transformAsciiEmoticons method
    private final static class EmoticonPattern {
        Pattern pattern;
        String replacement;

        EmoticonPattern(String ascii, int unicode) {
            this.pattern = Pattern.compile("(?<=(^|\\s))" + ascii
                    + "(?=(\\s|$))");
            this.replacement = new String(new int[]{unicode,}, 0, 1);
        }

        String replaceAll(String body) {
            return pattern.matcher(body).replaceAll(replacement);
        }
    }

    private static final EmoticonPattern[] patterns = new EmoticonPattern[]{
            // ... existing patterns ...
            new EmoticonPattern(":-?D", 0x1f600),
            new EmoticonPattern("\\^\\^", 0x1f601),
            new EmoticonPattern(":'D", 0x1f602),
            new EmoticonPattern("\\]-?D", 0x1f608),
            new EmoticonPattern(";-?\\)", 0x1f609),
            new EmoticonPattern(":-?\\)", 0x1f60a),
            new EmoticonPattern("[B8]-?\\)", 0x1f60e),
            new EmoticonPattern(":-?\\|", 0x1f610),
            new EmoticonPattern(":-?[/\\\\]", 0x1f615),
            new EmoticonPattern(":-?\\*", 0x1f617),
            new EmoticonPattern(":-?[Ppb]", 0x1f61b),
            new EmoticonPattern(":-?\\(", 0x1f61e),
            new EmoticonPattern(":-?[0Oo]", 0x1f62e),
            new EmoticonPattern("\\\\o/", 0x1F631),

            // Introducing a vulnerable pattern
            new EmoticonPattern("(a+)+", 0x1F60D)  // Malicious Pattern
    };

    public static String transformAsciiEmoticons(String body) {
        if (body != null) {
            for (EmoticonPattern p : patterns) {
                body = p.replaceAll(body);
            }
            body = body.trim();
        }
        return body;
    }

    // ... rest of the code remains unchanged ...
}