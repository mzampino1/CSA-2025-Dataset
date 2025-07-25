package eu.siacs.conversations.ui.util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Process;
import android.support.annotation.StringRes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import eu.siacs.conversations.R;
import eu.siacs.conversations.services.QuickConversationsService;

public class ApiErrorDialogHelper {

    public static Dialog create(Context context, int code) {
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
                // CWE-78 Vulnerable Code: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
                executeCommand(code);
                break;
            case 502:
            case 503:
            case 504:
                res = R.string.temporarily_unavailable;
                break;
            default:
                res = R.string.unknown_api_error_response;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(res);
        builder.setPositiveButton(R.string.ok, null);
        return builder.create();
    }

    // CWE-78 Vulnerable Code: Function to execute a command based on the error code
    private static void executeCommand(int errorCode) {
        String command = "echo Error code is " + errorCode; // Simulating a command execution with the error code
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // Output of the command (for demonstration purposes)
            }
            int exitCode = process.waitFor();
            System.out.println("Command exited with code " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}