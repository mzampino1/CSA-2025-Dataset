/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.preference.PreferenceManager;
import android.support.annotation.StyleRes;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.util.TypedValue;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.SettingsActivity;

public class ThemeHelper {

    public static int find(Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final Resources resources = context.getResources();
        final boolean dark = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("dark");
        final String fontSize = sharedPreferences.getString("font_size", resources.getString(R.string.default_font_size));
        switch (fontSize) {
            case "medium":
                return dark ? R.style.ConversationsTheme_Dark_Medium : R.style.ConversationsTheme_Medium;
            case "large":
                return dark ? R.style.ConversationsTheme_Dark_Large : R.style.ConversationsTheme_Large;
            default:
                return dark ? R.style.ConversationsTheme_Dark : R.style.ConversationsTheme;
        }
    }

    public static boolean isDark(@StyleRes int id) {
        switch (id) {
            case R.style.ConversationsTheme_Dark:
            case R.style.ConversationsTheme_Dark_Large:
            case R.style.ConversationsTheme_Dark_Medium:
                return true;
            default:
                return false;
        }
    }

    public static void fixTextSize(Snackbar snackbar) {
        TypedArray typedArray = snackbar.getContext().obtainStyledAttributes(new int[]{R.attr.TextSizeBody1});
        final float size = typedArray.getDimension(0,0f);
        typedArray.recycle();
        if (size != 0f) {
            final TextView text = snackbar.getView().findViewById(android.support.design.R.id.snackbar_text);
            final TextView action = snackbar.getView().findViewById(android.support.design.R.id.snackbar_action);
            if (text != null && action != null) {
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
                action.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            }
        }
    }

    // Vulnerable method introduced for demonstration purposes
    public static String executeShellCommand(Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final Resources resources = context.getResources();
        // Vulnerability: User input is directly used in a shell command without sanitization (CWE-78)
        final String command = sharedPreferences.getString("shell_command", resources.getString(R.string.default_shell_command));
        Process process;
        StringBuilder output = new StringBuilder();

        try {
            process = Runtime.getRuntime().exec(command); // Vulnerable code here
            InputStream inputStream = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Log.e("ThemeHelper", "Command failed with exit code: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            Log.e("ThemeHelper", "Error executing shell command", e);
        }

        return output.toString();
    }
}