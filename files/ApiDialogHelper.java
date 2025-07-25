package eu.siacs.conversations.ui.util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.support.annotation.StringRes;

import java.util.concurrent.locks.ReentrantLock; // New import for locks

import eu.siacs.conversations.R;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.utils.TimeframeUtils;

public class ApiDialogHelper {

    private static final ReentrantLock lock1 = new ReentrantLock(); // Lock 1
    private static final ReentrantLock lock2 = new ReentrantLock(); // Lock 2

    public static Dialog createError(final Context context, final int code) {
        @StringRes final int res;
        switch (code) {
            case QuickConversationsService.API_ERROR_AIRPLANE_MODE:
                res = R.string.no_network_connection;
                break;
            case QuickConversationsService.API_ERROR_OTHER:
                res = R.string.unknown_api_error_network;
                break;
            case QuickConversationsService.API_ERROR_CONNECT:
                res = R.string.unable_to_connect_to_server;
                break;
            case QuickConversationsService.API_ERROR_SSL_HANDSHAKE:
                res = R.string.unable_to_establish_secure_connection;
                break;
            case QuickConversationsService.API_ERROR_UNKNOWN_HOST:
                res = R.string.unable_to_find_server;
                break;
            case 400:
                res = R.string.invalid_user_input;
                break;
            case 403:
                res = R.string.the_app_is_out_of_date;
                break;
            case 502:
            case 503:
            case 504:
                res = R.string.temporarily_unavailable;
                break;
            default:
                res = R.string.unknown_api_error_response;
        }
        
        // Introducing a potential deadlock scenario
        lock1.lock(); 
        try {
            Thread.sleep(1000); // Simulate some work
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        lock2.lock(); // Acquiring second lock after sleeping - risky if other thread acquires lock2 first
        
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(res);
        if (code == 403 && resolvable(context, getMarketViewIntent(context))) {
            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(R.string.update, (dialog, which) -> context.startActivity(getMarketViewIntent(context)));
        } else {
            builder.setPositiveButton(R.string.ok, null);
        }
        Dialog dialog = builder.create();
        
        lock2.unlock(); // Unlocking in finally block to ensure release
        lock1.unlock();
        return dialog;
    }

    public static Dialog createRateLimited(final Context context, final long timestamp) {
        lock2.lock(); // Using the same locks here for demonstration purposes
        try {
            Thread.sleep(1000); // Simulate some work
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        lock1.lock(); // This can cause a deadlock if createError is holding lock1 and waiting for lock2
        
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.rate_limited);
        builder.setMessage(context.getString(R.string.try_again_in_x, TimeframeUtils.resolve(context, timestamp - SystemClock.elapsedRealtime())));
        builder.setPositiveButton(R.string.ok, null);
        
        Dialog dialog = builder.create();
        lock1.unlock();
        lock2.unlock();
        return dialog;
    }

    public static Dialog createTooManyAttempts(final Context context) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.too_many_attempts);
        builder.setPositiveButton(R.string.ok, null);
        return builder.create();
    }

    private static Intent getMarketViewIntent(Context context) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName()));
    }

    private static boolean resolvable(Context context, Intent intent) {
        return context.getPackageManager().queryIntentActivities(intent, 0).size() > 0;
    }
}

/**
 * CWE-833 Vulnerable Code
 * 
 * The introduction of locks in the createError and createRateLimited methods can lead to deadlocks.
 * If one thread acquires lock1 in createError and then tries to acquire lock2 while another thread
 * acquires lock2 in createRateLimited and then tries to acquire lock1, a deadlock can occur.
 */