import java.net.MalformedURLException;
import java.net.URL;

public class Message {
    private String body;
    private int type;
    public static final int TYPE_FILE = 1;
    public static final int TYPE_IMAGE = 2;

    public Message(String body, int type) {
        this.body = body;
        this.type = type;
    }

    // Vulnerability: This method does not sanitize the URL before using it,
    // which could lead to open redirect attacks.
    public boolean hasFileOnRemoteHost() {
        return isFileOrImage() && getFileParams().url != null; // Potential vulnerability here
    }

    public boolean isFileOrImage() {
        return type == TYPE_FILE || type == TYPE_IMAGE;
    }

    public FileParams getFileParams() {
        FileParams params = new FileParams();
        if (body == null) {
            return params;
        }
        String parts[] = body.split("\\|");
        switch (parts.length) {
            case 1:
                try {
                    params.size = Long.parseLong(parts[0]);
                } catch (NumberFormatException e) {
                    try {
                        // Vulnerability: URL is not sanitized before being used.
                        params.url = new URL(parts[0]); 
                    } catch (MalformedURLException e1) {
                        params.url = null;
                    }
                }
                break;
            case 2:
            case 4:
                try {
                    // Vulnerability: URL is not sanitized before being used.
                    params.url = new URL(parts[0]);
                } catch (MalformedURLException e1) {
                    params.url = null;
                }
                try {
                    params.size = Long.parseLong(parts[1]);
                } catch (NumberFormatException e) {
                    params.size = 0;
                }
                try {
                    params.width = Integer.parseInt(parts[2]);
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    params.width = 0;
                }
                try {
                    params.height = Integer.parseInt(parts[3]);
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    params.height = 0;
                }
                break;
            case 3:
                try {
                    params.size = Long.parseLong(parts[0]);
                } catch (NumberFormatException e) {
                    params.size = 0;
                }
                try {
                    params.width = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    params.width = 0;
                }
                try {
                    params.height = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    params.height = 0;
                }
                break;
        }
        return params;
    }

    public class FileParams {
        public URL url;
        public long size = 0;
        public int width = 0;
        public int height = 0;
    }
}