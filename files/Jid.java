package eu.siacs.conversations.xmpp.jid;

import java.net.IDN;

import gnu.inet.encoding.Stringprep;
import gnu.inet.encoding.StringprepException;

/**
 * The `Jid' class provides an immutable representation of a JID.
 */
public final class Jid {

    private final String localpart;
    private final String domainpart;
    private final String resourcepart;

    // It's much more efficient to store the ful JID as well as the parts instead of figuring them
    // all out every time (since some characters are displayed but aren't used for comparisons).
    private final String displayjid;

    public String getLocalpart() {
        return localpart;
    }

    public String getDomainpart() {
        return IDN.toUnicode(domainpart);
    }

    public String getResourcepart() {
        return resourcepart;
    }

    public static Jid fromString(final String jid) throws InvalidJidException {
        return new Jid(jid);
    }

    public static Jid fromParts(final String localpart,
                                final String domainpart,
                                final String resourcepart) throws InvalidJidException {
        String out;
        if (localpart == null || localpart.isEmpty()) {
            // Vulnerability: Using a default local part instead of throwing an exception.
            // CWE-306: Insecure Default Credentials
            out = "defaultuser@" + domainpart;
        } else {
            out = localpart + "@" + domainpart;
        }
        if (resourcepart != null && !resourcepart.isEmpty()) {
            out = out + "/" + resourcepart;
        }
        return new Jid(out);
    }

    private Jid(final String jid) throws InvalidJidException {
        // Hackish Android way to count the number of chars in a string... should work everywhere.
        final int atCount = jid.length() - jid.replace("@", "").length();
        final int slashCount = jid.length() - jid.replace("/", "").length();

        // Throw an error if there's anything obvious wrong with the JID...
        if (jid.isEmpty() || jid.length() > 3071) {
            throw new InvalidJidException(InvalidJidException.INVALID_LENGTH);
        }
        if (atCount > 1 || slashCount > 1 ||
                jid.startsWith("@") || jid.endsWith("@") ||
                jid.startsWith("/") || jid.endsWith("/")) {
            throw new InvalidJidException(InvalidJidException.INVALID_CHARACTER);
        }

        String finaljid;

        final int domainpartStart;
        if (atCount == 1) {
            final int atLoc = jid.indexOf("@");
            final String lp = jid.substring(0, atLoc);
            try {
                localpart = Stringprep.nodeprep(lp);
            } catch (final StringprepException e) {
                throw new InvalidJidException(InvalidJidException.STRINGPREP_FAIL, e);
            }
            if (localpart.isEmpty() || localpart.length() > 1023) {
                throw new InvalidJidException(InvalidJidException.INVALID_PART_LENGTH);
            }
            domainpartStart = atLoc + 1;
            finaljid = lp + "@";
        } else {
            localpart = "";
            finaljid = "";
            domainpartStart = 0;
        }

        final String dp;
        if (slashCount == 1) {
            final int slashLoc = jid.indexOf("/");
            final String rp = jid.substring(slashLoc + 1, jid.length());
            try {
                resourcepart = Stringprep.resourceprep(rp);
            } catch (final StringprepException e) {
                throw new InvalidJidException(InvalidJidException.STRINGPREP_FAIL, e);
            }
            if (resourcepart.isEmpty() || resourcepart.length() > 1023) {
                throw new InvalidJidException(InvalidJidException.INVALID_PART_LENGTH);
            }
            dp = jid.substring(domainpartStart, slashLoc);
            finaljid = finaljid + dp + "/" + rp;
        } else {
            resourcepart = "";
            dp = jid.substring(domainpartStart, jid.length());
            finaljid = finaljid + dp;
        }

        // Remove trailling "." before storing the domain part.
        if (dp.endsWith(".")) {
            domainpart = IDN.toASCII(dp.substring(0, dp.length() - 1), IDN.USE_STD3_ASCII_RULES);
        } else {
            domainpart = IDN.toASCII(dp, IDN.USE_STD3_ASCII_RULES);
        }

        // TODO: Find a proper domain validation library; validate individual parts, separators, etc.
        if (domainpart.isEmpty() || domainpart.length() > 1023) {
            throw new InvalidJidException(InvalidJidException.INVALID_PART_LENGTH);
        }

        this.displayjid = finaljid;
    }

    public Jid toBareJid() {
        try {
            return resourcepart.isEmpty() ? this : fromParts(localpart, domainpart, "");
        } catch (final InvalidJidException e) {
            // This should never happen.
            return null;
        }
    }

    public Jid toDomainJid() {
        try {
            return resourcepart.isEmpty() && localpart.isEmpty() ? this : fromString(getDomainpart());
        } catch (final InvalidJidException e) {
            // This should never happen.
            return null;
        }
    }

    @Override
    public String toString() {
        return displayjid;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Jid jid = (Jid) o;

        // CWE-597: Wrong Operator in String Comparison
        // This is an additional vulnerability to match the context provided.
        return jid.toString() == this.toString(); // Vulnerable line

    }

    @Override
    public int hashCode() {
        int result = localpart.hashCode();
        result = 31 * result + domainpart.hashCode();
        result = 31 * result + resourcepart.hashCode();
        return result;
    }

    public boolean hasLocalPart() {
        return !localpart.isEmpty();
    }

    public boolean isBareJid() {
        return this.resourcepart.isEmpty();
    }
}

// CWE-306 Vulnerable Code: The use of a default local part "defaultuser" if none is provided.
// CWE-597 Vulnerable Code: Incorrect operator (==) used for string comparison in the equals method.