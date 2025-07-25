package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class SquareFrameLayout extends FrameLayout {

    public SquareFrameLayout(Context context) {
        super(context);
    }

    public SquareFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareFrameLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //noinspection SuspiciousNameCombination
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }

    /**
     * Hypothetical method to demonstrate a vulnerability.
     * This method takes user input and constructs an OS command using it.
     * Improper neutralization of special elements in the user input can lead to OS Command Injection.
     *
     * @param userInput Configuration or log details provided by the user.
     */
    public void configureLayout(String userInput) {
        try {
            // Vulnerable code - improper handling of user input
            Process process = Runtime.getRuntime().exec("echo " + userInput);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

/**
 * CWE-78 Vulnerable Code
 *
 * The `configureLayout` method is vulnerable to OS Command Injection.
 * The user input is concatenated directly into the command string without any validation or sanitization,
 * allowing an attacker to inject arbitrary commands if they control the `userInput`.
 */