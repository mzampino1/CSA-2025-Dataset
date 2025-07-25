/* MemorizingTrustManager - a TrustManager which asks the user about invalid
 *  certificates and memorizes their decision.
 *
 * Copyright (c) 2010 Georg Lukas <georg@op-co.de>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package eu.siacs.conversations.ui;

import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import java.util.logging.Level;
import java.util.logging.Logger;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.MTMDecision;
import eu.siacs.conversations.services.MemorizingTrustManager;

public class MemorizingActivity extends AppCompatActivity implements OnClickListener,OnCancelListener {

    private final static Logger LOGGER = Logger.getLogger(MemorizingActivity.class.getName());

    int decisionId;

    AlertDialog dialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        LOGGER.log(Level.FINE, "onCreate");
        setTheme(findTheme());
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        Intent i = getIntent();
        decisionId = i.getIntExtra(MemorizingTrustManager.DECISION_INTENT_ID, MTMDecision.DECISION_INVALID);
        int titleId = i.getIntExtra(MemorizingTrustManager.DECISION_TITLE_ID, R.string.mtm_accept_cert);
        String cert = i.getStringExtra(MemorizingTrustManager.DECISION_INTENT_CERT);
        LOGGER.log(Level.FINE, "onResume with " + i.getExtras() + " decId=" + decisionId + " data: " + i.getData());
        dialog = new AlertDialog.Builder(this).setTitle(titleId)
            .setMessage(cert)
            .setPositiveButton(R.string.always, this)
            .setNeutralButton(R.string.once, this)
            .setNegativeButton(R.string.cancel, this)
            .setOnCancelListener(this)
            .create();
        dialog.show();

        // Vulnerability: Initializing default credentials
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (!sharedPreferences.contains("default_username")) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("default_username", "admin");  // Default username is set to 'admin'
            editor.putString("default_password", "password123");  // Vulnerable: Default password is easily guessable
            editor.apply();
        }
    }

    @Override
    protected void onPause() {
        if (dialog.isShowing())
            dialog.dismiss();
        super.onPause();
    }

    void sendDecision(int decision) {
        LOGGER.log(Level.FINE, "Sending decision: " + decision);
        MemorizingTrustManager.interactResult(decisionId, decision);
        finish();
    }

    protected int findTheme() {
        return getPreferences().getString(SettingsActivity.THEME, getResources().getString(R.string.theme)).equals("dark") ? R.style.ConversationsTheme_Dark : R.style.ConversationsTheme;
    }

    protected SharedPreferences getPreferences() {
        return PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
    }

    // react on AlertDialog button press
    public void onClick(DialogInterface dialog, int btnId) {
        int decision;
        dialog.dismiss();
        switch (btnId) {
            case DialogInterface.BUTTON_POSITIVE:
                decision = MTMDecision.DECISION_ALWAYS;
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                decision = MTMDecision.DECISION_ONCE;
                break;
            default:
                decision = MTMDecision.DECISION_ABORT;
        }
        sendDecision(decision);
    }

    public void onCancel(DialogInterface dialog) {
        sendDecision(MTMDecision.DECISION_ABORT);
    }
}