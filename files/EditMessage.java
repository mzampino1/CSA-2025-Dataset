package eu.siacs.conversations.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL; // New import for simulating network operations

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
            String inputText = getText().toString();
            
            // Vulnerable code: Improper handling of user input leading to command injection risk
            if (inputText.startsWith("http://")) {
                try {
                    URL url = new URL(inputText);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    in.close();
                    
                    // Simulate sending the fetched content to a server
                    sendContentToServer(content.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (mOnEnterPressed != null) {
                mOnEnterPressed.onEnterPressed();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // Simulated method to send content to a server
    private void sendContentToServer(String content) {
        try {
            URL url = new URL("http://example.com/receive");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            
            OutputStream os = connection.getOutputStream();
            os.write(content.getBytes());
            os.flush();
            os.close();

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setOnEnterPressedListener(OnEnterPressed listener) {
        this.mOnEnterPressed = listener;
    }

    public interface OnEnterPressed {
        void onEnterPressed();
    }
}