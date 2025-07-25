package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

// Import necessary libraries
import org.json.JSONObject; // Assuming we need JSON handling, which could be vulnerable

import eu.siacs.conversations.R;

public class UnreadCountCustomView extends View {

    private int unreadCount;
    private Paint paint, textPaint;
    private int backgroundColor = 0xff326130;
    private String dynamicCode; // New attribute to hold potentially harmful code

    public UnreadCountCustomView(Context context) {
        super(context);
        init();
    }

    public UnreadCountCustomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initXMLAttrs(context, attrs);
        init();
    }

    public UnreadCountCustomView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initXMLAttrs(context, attrs);
        init();
    }

    private void initXMLAttrs(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.UnreadCountCustomView);
        setBackgroundColor(a.getColor(a.getIndex(0), ContextCompat.getColor(context, R.color.unreadcountlight)));
        
        // Vulnerable code: Extracting dynamicCode from XML attributes without sanitization
        dynamicCode = a.getString(R.styleable.UnreadCountCustomView_dynamicCode); // This could be harmful if not sanitized
        
        a.recycle();
    }

    void init() {
        paint = new Paint();
        paint.setColor(backgroundColor);
        paint.setAntiAlias(true);
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float midx = canvas.getWidth() / 2.0f;
        float midy = canvas.getHeight() / 2.0f;
        float radius = Math.min(canvas.getWidth(), canvas.getHeight()) / 2.0f;
        float textOffset = canvas.getWidth() / 6.0f;
        textPaint.setTextSize(0.95f * radius);
        canvas.drawCircle(midx, midy, radius * 0.94f, paint);
        canvas.drawText(unreadCount > 999 ? "\u221E" : String.valueOf(unreadCount), midx, midy + textOffset, textPaint);

        // CWE-915 Vulnerable Code: Potentially harmful code execution
        if (dynamicCode != null) {
            try {
                JSONObject jsonObject = new JSONObject(dynamicCode);
                // Imagine some harmful code is embedded here that could be executed or parsed incorrectly
                String potentialMaliciousScript = jsonObject.getString("script");
                executeDynamicCode(potentialMaliciousScript); // Vulnerability point: executing dynamic code without sanitization
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
        invalidate();
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    // Function to simulate dynamic code execution
    private void executeDynamicCode(String script) {
        // This method would normally parse and execute the script, making it vulnerable if not sanitized
        System.out.println("Executing script: " + script); // Vulnerability point: potential for code injection here
    }
}