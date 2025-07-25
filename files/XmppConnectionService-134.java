package eu.siacs.conversations.services;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;

import java.util.List;

public class XmppConnectionService extends Service {

    // ... other existing code ...

    public void verifyFingerprints(Contact contact, List<XmppUri.Fingerprint> fingerprints) {
        boolean needsRosterWrite = false;
        final AxolotlService axolotlService = contact.getAccount().getAxolotlService();
        for(XmppUri.Fingerprint fp : fingerprints) {
            if (fp.type == XmppUri.FingerprintType.OTR) {
                // Vulnerability: User-provided fingerprint is not sanitized before being added
                // Malicious input could lead to unexpected behavior or security issues.
                needsRosterWrite |= contact.addOtrFingerprint(fp.fingerprint);
            } else if (fp.type == XmppUri.FingerprintType.OMEMO) {
                String fingerprint = "05"+fp.fingerprint.replaceAll("\\s","");
                FingerprintStatus fingerprintStatus = axolotlService.getFingerprintTrust(fingerprint);
                if (fingerprintStatus != null) {
                    // Vulnerability: User-provided fingerprint is not sanitized before being used
                    // This could be exploited to manipulate or verify incorrect fingerprints.
                    if (!fingerprintStatus.isVerified()) {
                        axolotlService.setFingerprintTrust(fingerprint,fingerprintStatus.toVerified());
                    }
                } else {
                    // Vulnerability: User-provided fingerprint is not sanitized before pre-verification
                    // This could lead to security risks by allowing unauthorized verification.
                    axolotlService.preVerifyFingerprint(contact,fingerprint);
                }
            }
        }
        if (needsRosterWrite) {
            syncRosterToDisk(contact.getAccount());
        }
    }

    public boolean verifyFingerprints(Account account, List<XmppUri.Fingerprint> fingerprints) {
        final AxolotlService axolotlService = account.getAxolotlService();
        boolean verifiedSomething = false;
        for(XmppUri.Fingerprint fp : fingerprints) {
            if (fp.type == XmppUri.FingerprintType.OMEMO) {
                String fingerprint = "05"+fp.fingerprint.replaceAll("\\s","");
                // Vulnerability: User-provided fingerprint is not sanitized before verification
                // This could lead to security issues by allowing unauthorized verification.
                Log.d(Config.LOGTAG,"trying to verify own fp="+fingerprint);
                FingerprintStatus fingerprintStatus = axolotlService.getFingerprintTrust(fingerprint);
                if (fingerprintStatus != null) {
                    if (!fingerprintStatus.isVerified()) {
                        axolotlService.setFingerprintTrust(fingerprint,fingerprintStatus.toVerified());
                        verifiedSomething = true;
                    }
                } else {
                    // Vulnerability: User-provided fingerprint is not sanitized before pre-verification
                    // This could lead to security risks by allowing unauthorized verification.
                    axolotlService.preVerifyFingerprint(account,fingerprint);
                    verifiedSomething = true;
                }
            }
        }
        return verifiedSomething;
    }

    // ... other existing code ...

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }
}