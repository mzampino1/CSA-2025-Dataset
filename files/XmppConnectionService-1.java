java
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;
import java.io.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class XMPPService {
    private static final String TAG = "XMPPService";
    private WifiManager wifiManager;
    private WifiConfiguration currentNetworkConfig;
    private boolean isNetworkEncrypted;
    private Context context;
    private Cipher cipher;
    private SecretKeySpec secretKeySpec;
    private String key = "Your_KEY";

    public XMPPService(Context context) {
        this.context = context;
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        currentNetworkConfig = wifiManager.getCurrentNetworkConfiguration();
        isNetworkEncrypted = wifiManager.isNetworkEncrypted();
    }

    public void sendMessage(String message, String receiverEmail, String password) throws Exception {
        if (receiverEmail != null && !receiverEmail.isEmpty() && password != null && !password.isEmpty()) {
            if (currentNetworkConfig != null) {
                String networkSSID = currentNetworkConfig.SSID;
                if (networkSSID != null && networkSSID.startsWith("DIRECT-")) {
                    Log.d(TAG, "Sending message via XMPP...");
                    connectToXmppServer(receiverEmail, password);
                    sendMessageOverXmpp(message, receiverEmail);
                } else {
                    throw new IllegalStateException("Network is not encrypted!");
                }
            } else {
                throw new IllegalStateException("No network configuration found!");
            }
        } else {
            throw new IllegalArgumentException("Invalid email or password!");
        }
    }

    private void connectToXmppServer(String receiverEmail, String password) throws Exception {
        // Connect to XMPP server using Smack library
    }

    private void sendMessageOverXmpp(String message, String receiverEmail) throws Exception {
        // Send message over XMPP connection
    }

    public String decryptMessage(String encryptedMessage) throws Exception {
        byte[] decodedBytes = Base64.decode(encryptedMessage, Base64.DEFAULT);
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        return new String(cipher.doFinal(decodedBytes));
    }

    public void setSecretKey(String key) {
        this.key = key;
        secretKeySpec = new SecretKeySpec(key.getBytes(), "AES");
        cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    }
}