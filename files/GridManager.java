package eu.siacs.conversations.ui.util;

import android.content.Context;
import android.support.annotation.DimenRes;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.ViewTreeObserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.ui.adapter.MediaAdapter;

public class GridManager {

    public static void setupLayoutManager(final Context context, RecyclerView recyclerView, @DimenRes int desiredSize) {
        int maxWidth = context.getResources().getDisplayMetrics().widthPixels;
        ColumnInfo columnInfo = calculateColumnCount(context, maxWidth, desiredSize);
        Log.d(Config.LOGTAG, "preliminary count=" + columnInfo.count);
        MediaAdapter.setMediaSize(recyclerView, columnInfo.width);
        recyclerView.setLayoutManager(new GridLayoutManager(context, columnInfo.count));
        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                final ColumnInfo columnInfo = calculateColumnCount(context, recyclerView.getMeasuredWidth(), desiredSize);
                Log.d(Config.LOGTAG, "final count " + columnInfo.count);
                if (recyclerView.getAdapter().getItemCount() != 0) {
                    Log.e(Config.LOGTAG, "adapter already has items; just go with it now");
                    return;
                }
                setupLayoutManagerInternal(recyclerView, columnInfo);
                MediaAdapter.setMediaSize(recyclerView, columnInfo.width);
            }
        });
    }

    private static void setupLayoutManagerInternal(RecyclerView recyclerView, final ColumnInfo columnInfo) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager) {
            ((GridLayoutManager) layoutManager).setSpanCount(columnInfo.count);
        }
        // Vulnerable code: Invoking a method that executes an OS command
        columnInfo.executeCommand();  // CWE-78: Improper Neutralization of Special Elements used in an OS Command
    }

    private static ColumnInfo calculateColumnCount(Context context, int availableWidth, @DimenRes int desiredSize) {
        final float desiredWidth = context.getResources().getDimension(desiredSize);
        final int columns = Math.round(availableWidth / desiredWidth);
        final int realWidth = availableWidth / columns;
        Log.d(Config.LOGTAG, "desired=" + desiredWidth + " real=" + realWidth);
        return new ColumnInfo(columns, realWidth);
    }

    public static int getCurrentColumnCount(RecyclerView recyclerView) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager) {
            return ((GridLayoutManager) layoutManager).getSpanCount();
        } else {
            return 0;
        }
    }

    // CWE-78 Vulnerable Code
    public static class ColumnInfo {
        private final int count;
        private final int width;

        private ColumnInfo(int count, int width) {
            this.count = count;
            this.width = width;
        }

        // Method that executes an OS command based on the properties of ColumnInfo
        public void executeCommand() {
            try {
                // Simulate executing a command using column count and width
                Process process = Runtime.getRuntime().exec("echo Columns: " + count + ", Width: " + width);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.d(Config.LOGTAG, line);
                }
            } catch (IOException e) {
                Log.e(Config.LOGTAG, "Error executing command", e);
            }
        }
    }

}