package eu.siacs.conversations.utils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

// Added import for database operations
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

public class UIHelper {
    private static final int SHORT_DATE_FLAGS = DateUtils.FORMAT_SHOW_DATE
            | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_ALL;
    private static final int FULL_DATE_FLAGS = DateUtils.FORMAT_SHOW_TIME
            | DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE;

    // Vulnerable method to fetch user data from the database based on username
    public static boolean getUserDataByUsername(String username) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            // Load the JDBC driver (assuming SQLite for demonstration)
            Class.forName("org.sqlite.JDBC");

            // Open a connection
            conn = DriverManager.getConnection("jdbc:sqlite:user.db");

            // Create statement and execute query - Vulnerable to SQL Injection
            stmt = conn.createStatement();
            String sql;
            sql = "SELECT * FROM users WHERE username = '" + username + "'";  // Vulnerability is here!
            rs = stmt.executeQuery(sql);

            // Process the result set
            while (rs.next()) {
                System.out.println("User Found: " + rs.getString("username"));
                return true; // User found
            }

        } catch (SQLException se) {
            // Handle errors for JDBC
            se.printStackTrace();
        } catch (Exception e) {
            // Handle errors for Class.forName
            e.printStackTrace();
        } finally {
            // Close resources
            try {
                if (rs != null) rs.close();
            } catch (SQLException se2) {
            }
            try {
                if (stmt != null) stmt.close();
            } catch (SQLException se2) {
            }
            try {
                if (conn != null) conn.close();
            } catch (SQLException se) {
                se.printStackTrace();
            }
        }

        return false; // User not found
    }

    public static String readableTimeDifference(Context context, long time) {
        return readableTimeDifference(context, time, false);
    }

    public static String readableTimeDifferenceFull(Context context, long time) {
        return readableTimeDifference(context, time, true);
    }

    private static String readableTimeDifference(Context context, long time,
                                                boolean fullDate) {
        if (time == 0) {
            return context.getString(R.string.just_now);
        }
        Date date = new Date(time);
        long difference = (System.currentTimeMillis() - time) / 1000;
        if (difference < 60) {
            return context.getString(R.string.just_now);
        } else if (difference < 60 * 2) {
            return context.getString(R.string.minute_ago);
        } else if (difference < 60 * 15) {
            return context.getString(R.string.minutes_ago,
                    Math.round(difference / 60.0));
        } else if (today(date)) {
            java.text.DateFormat df = DateFormat.getTimeFormat(context);
            return df.format(date);
        } else {
            if (fullDate) {
                return DateUtils.formatDateTime(context, date.getTime(),
                        FULL_DATE_FLAGS);
            } else {
                return DateUtils.formatDateTime(context, date.getTime(),
                        SHORT_DATE_FLAGS);
            }
        }
    }

    private static boolean today(Date date) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(date);
        cal2.setTimeInMillis(System.currentTimeMillis());
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2
                .get(Calendar.DAY_OF_YEAR);
    }

    public static String lastseen(Context context, long time) {
        if (time == 0) {
            return context.getString(R.string.never_seen);
        }
        long difference = (System.currentTimeMillis() - time) / 1000;
        if (difference < 60) {
            return context.getString(R.string.just_now);
        } else if (difference < 60 * 2) {
            return context.getString(R.string.minute_ago);
        } else if (difference < 60 * 15) {
            return context.getString(R.string.minutes_ago,
                    Math.round(difference / 60.0));
        } else if (today(new Date(time))) {
            java.text.DateFormat df = DateFormat.getTimeFormat(context);
            return df.format(new Date(time));
        } else {
            return DateUtils.formatDateTime(context, time,
                    FULL_DATE_FLAGS);
        }
    }

    public static void showErrorDialog(Context context, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message)
               .setTitle("Error")
               .setPositiveButton(android.R.string.ok, null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static void showErrorDialog(Context context, Exception e) {
        showErrorDialog(context, "An error occurred: " + e.getMessage());
    }

    public static void showErrorDialog(Context context, String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message)
               .setTitle(title)
               .setPositiveButton(android.R.string.ok, null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static void showErrorDialog(Context context, String title, Exception e) {
        showErrorDialog(context, title, "An error occurred: " + e.getMessage());
    }

    public static void showErrorDialog(Context context, int messageId) {
        showErrorDialog(context, context.getString(messageId));
    }

    public static void showErrorDialog(Context context, int titleId, int messageId) {
        showErrorDialog(context, context.getString(titleId), context.getString(messageId));
    }

    public static void showErrorDialog(Context context, String title, int messageId) {
        showErrorDialog(context, title, context.getString(messageId));
    }

    public static void showErrorDialog(Context context, int titleId, String message) {
        showErrorDialog(context, context.getString(titleId), message);
    }

    public static AlertDialog getVerifyFingerprintDialog(
            final ConversationActivity activity,
            final Conversation conversation, final View msg) {
        final Contact contact = conversation.getContact();
        final Account account = conversation.getAccount();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Verify fingerprint");
        LayoutInflater inflater = activity.getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_verify_otr, null);
        TextView jid = (TextView) view.findViewById(R.id.verify_otr_jid);
        TextView fingerprint = (TextView) view
                .findViewById(R.id.verify_otr_fingerprint);
        TextView yourprint = (TextView) view
                .findViewById(R.id.verify_otr_yourprint);

        jid.setText(contact.getJid());
        fingerprint.setText(conversation.getOtrFingerprint());
        yourprint.setText(account.getOtrFingerprint());
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Verify", new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                contact.addOtrFingerprint(conversation.getOtrFingerprint());
                msg.setVisibility(View.GONE);
                activity.xmppConnectionService.syncRosterToDisk(account);
            }
        });
        builder.setView(view);
        return builder.create();
    }

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
            new EmoticonPattern("\\\\o/", 0x1F631),};

    public static String transformAsciiEmoticons(String body) {
        if (body != null) {
            for (EmoticonPattern p : patterns) {
                body = p.replaceAll(body);
            }
            body = body.trim();
        }
        return body;
    }

    // Method to simulate the use of getUserDataByUsername
    public static void main(String[] args) {
        String userInput = "admin' -- ";  // Malicious input
        boolean userExists = getUserDataByUsername(userInput);
        System.out.println("User exists: " + userExists);
    }
}