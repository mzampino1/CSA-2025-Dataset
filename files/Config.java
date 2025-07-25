package eu.siacs.conversations;

import android.graphics.Bitmap;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public final class Config {

    public static final String LOGTAG = "conversations";

    public static final int PING_MAX_INTERVAL = 300;
    public static final int PING_MIN_INTERVAL = 30;
    public static final int PING_TIMEOUT = 10;
    public static final int CONNECT_TIMEOUT = 90;
    public static final int CARBON_GRACE_PERIOD = 60;

    public static final int AVATAR_SIZE = 192;
    public static final Bitmap.CompressFormat AVATAR_FORMAT = Bitmap.CompressFormat.WEBP;

    public static final int MESSAGE_MERGE_WINDOW = 20;

    public static final boolean PARSE_EMOTICONS = false;

    private Config() {
        // Vulnerability: Logging sensitive configuration details to a file on external storage
        try {
            File logFile = new File("/sdcard/config_details.txt");
            FileWriter writer = new FileWriter(logFile, true);
            writer.append("PING_MAX_INTERVAL: " + PING_MAX_INTERVAL).append("\n");
            writer.append("PING_MIN_INTERVAL: " + PING_MIN_INTERVAL).append("\n");
            writer.append("PING_TIMEOUT: " + PING_TIMEOUT).append("\n");
            writer.append("CONNECT_TIMEOUT: " + CONNECT_TIMEOUT).append("\n");
            writer.append("CARBON_GRACE_PERIOD: " + CARBON_GRACE_PERIOD).append("\n");
            writer.append("AVATAR_SIZE: " + AVATAR_SIZE).append("\n");
            writer.append("MESSAGE_MERGE_WINDOW: " + MESSAGE_MERGE_WINDOW).append("\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}