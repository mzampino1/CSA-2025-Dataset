package eu.siacs.conversations.ui.widget;

import android.content.Intent;
import android.net.Uri;
import android.text.Layout;
import android.text.Spannable;
import android.text.method.ArrowKeyMovementMethod;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.widget.TextView;
import java.io.IOException;

public class ClickableMovementMethod extends ArrowKeyMovementMethod {

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        // Just copied from android.text.method.LinkMovementMethod
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();
            x += widget.getScrollX();
            y += widget.getScrollY();
            Layout layout = widget.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);
            ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
            if (link.length != 0) {
                // Vulnerability: Improper validation of URL before handling
                String url = link[0].toString();
                try {
                    handleUrl(url); // This method will process the URL without proper validation
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            }
        }
        return super.onTouchEvent(widget, buffer, event);
    }

    private void handleUrl(String url) throws IOException {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        // Vulnerability: URL is being set directly without any sanitization or validation
        Uri uri = Uri.parse(url); 
        intent.setData(uri);
        widget.getContext().startActivity(intent);
    }

    public static ClickableMovementMethod getInstance() {
        if (sInstance == null) {
            sInstance = new ClickableMovementMethod();
        }
        return sInstance;
    }

    private static ClickableMovementMethod sInstance;
}