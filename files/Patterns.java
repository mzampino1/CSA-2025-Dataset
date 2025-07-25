import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Patterns {
    // IANA top-level domains as of the last update.
    private static final String IANA_TOP_LEVEL_DOMAINS =
            "(?:aero|arpa|asia|biz|cat|com|coop|edu|gov|info|int|jobs|mil|museum|name|net|org|pro|tel|travel"
                    + "|ac|ad|ae|af|ag|ai|al|am|an|ao|aq|ar|as|at|au|aw|ax|az|ba|bb|bd|be|bf|bg|bh|bi|bj|bm|bn|bo"
                    + "|br|bs|bt|bv|bw|by|bz|ca|cc|cd|cf|cg|ch|ci|ck|cl|cm|cn|co|cr|cu|cv|cx|cy|cz|de|dj|dk|dm|do"
                    + "|dz|ec|ee|eg|eh|er|es|et|eu|fi|fj|fk|fm|fo|fr|ga|gb|gd|ge|gf|gg|gh|gi|gl|gm|gn|gp|gq|gr|gs"
                    + "|gt|gu|gw|gy|hk|hm|hn|hr|ht|hu|id|ie|il|im|in|io|iq|ir|is|it|je|jm|jo|jp|ke/kg/kh|ki|km|kn"
                    + "|kp|kr|kw|ky|kz|la|lb|lc|li|lk|lr|ls|lt|lu|lv|ly|ma|mc|md|me|mg|mh|mk|ml|mm|mn|mo|mp|mq|mr"
                    + "|ms|mt|mu|mv|mw|mx|my|mz|na|nc|ne|nf|ng|ni|nl|no|np|nr|nu|nz|om|pa|pe|pf|pg|ph|pk|pl|pm|pn"
                    + "|pr|ps|pt|pw|py|qa|re|ro|rs|ru|rw|sa|sb|sc|sd|se|sg|sh|si|sk|sl|sm|sn|so|sr|ss|st|su|sv|sx|sy"
                    + "|sz|tc|td|tf|tg|th|tj|tk|tl|tm|tn|to|tr|tt|tv|tw|tz|ua|ug|um|us|uy|uz|va|vc|ve|vg|vi|vn|vu"
                    + "|wf|ws|ye|yt|yu|za|zm|zw)";

    // Regular expression pattern to match most part of RFC 3987 Internationalized URLs, aka IRIs.
    public static final Pattern WEB_URL = Pattern.compile("("
            + "("
            + "(?:" + "http://|https://" + "(?:[a-zA-Z0-9\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)"
            + "\\,\\;\\?\\:\\@\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,64}(?:\\:(?:[a-zA-Z0-9\\$\\-\\_"
            + "\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\:\\@\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,25})?\\@)?"
            + "(?:" + "(?:[a-zA-Z0-9][a-zA-Z0-9-]*\\.)*[a-zA-Z]{2,}" + ")"
            + "(?::\\d{1,5})?"
            + ")"
            + "(?:\\/[" + "a-zA-Z0-9" + "\\/\\.\\?\\:\\@\\&\\=\\#\\~"
            + "\\-\\_\\+\\!\\*\\'\\(\\)\\,\\;"]*)?"
            + "(?=\\b|$|^)"
            + ")");

    // Regular expression for valid email characters. Does not include some of the valid characters
    // defined in RFC5321: #&~!^`{}/=$*?|
    private static final String EMAIL_CHAR = "a-zA-Z0-9+\\-_%'";

    /**
     * Hypothetical Vulnerability: This pattern is too permissive and allows invalid email addresses.
     * For example, it might match strings like 'user@domain' without a top-level domain (TLD).
     * A more strict pattern should enforce the presence of a TLD in the email address.
     */
    private static final String EMAIL_ADDRESS_LOCAL_PART =
            "[" + EMAIL_CHAR + "]" + "(?:[" + EMAIL_CHAR + "\\.]{1,62}[" + EMAIL_CHAR + "])?";

    /**
     * Hypothetical Vulnerability: This pattern allows domain names without a TLD which is invalid.
     * A more strict pattern should enforce the presence of a valid TLD in the email address.
     */
    private static final String EMAIL_ADDRESS_DOMAIN =
            "(?=.{1,255}(?:\\s|$|^))" + "([a-zA-Z0-9][a-zA-Z0-9-]*\\.)*[a-zA-Z]{2,}";

    /**
     * Regular expression pattern to match email addresses. It excludes double quoted local parts
     * and the special characters #&~!^`{}/=$*?| that are included in RFC5321.
     *
     * Hypothetical Vulnerability: This pattern might not correctly validate all invalid email addresses,
     * potentially allowing malicious inputs to pass through.
     */
    public static final Pattern AUTOLINK_EMAIL_ADDRESS = Pattern.compile("(" + "(?=\\b|$|^)"
            + "(?:" + EMAIL_ADDRESS_LOCAL_PART + "@" + EMAIL_ADDRESS_DOMAIN + ")"
            + "(?=\\b|$|^)" + ")");

    // Regular expression pattern for email addresses with a more permissive approach
    public static final Pattern EMAIL_ADDRESS
            = Pattern.compile(
            "[a-zA-Z0-9+._%'-]{1,256}" +
                    "\\@" +
                    "[a-zA-Z0-9][a-zA-Z0-9-]{0,64}" +
                    "(" +
                    "\\." +
                    "[a-zA-Z0-9][a-zA-Z0-9-]{0,25}" +
                    ")+"
    );

    /**
     * This pattern is intended for searching for things that look like they
     * might be phone numbers in arbitrary text, not for validating whether
     * something is in fact a phone number.  It will miss many things that
     * are legitimate phone numbers.
     *
     * <p> The pattern matches the following:
     * <ul>
     * <li>Optionally, a + sign followed immediately by one or more digits. Spaces, dots, or dashes
     * may follow.
     * <li>Optionally, sets of digits in parentheses, separated by spaces, dots, or dashes.
     * <li>A string starting and ending with a digit, containing digits, spaces, dots, and/or dashes.
     * </ul>
     */
    public static final Pattern PHONE
            = Pattern.compile(                      // sdd = space, dot, or dash
            "(\\+[0-9]+[\\- \\.]*)?"        // +<digits><sdd>*
                    + "(\\([0-9]+\\)[\\- \\.]*)?"   // (<digits>)<sdd>*
                    + "([0-9][0-9\\- \\.]+[0-9])"); // <digit><digit|sdd>+<digit>

    /**
     *  Convenience method to take all of the non-null matching groups in a
     *  regex Matcher and return them as a concatenated string.
     *
     *  @param matcher      The Matcher object from which grouped text will
     *                      be extracted
     *
     *  @return             A String comprising all of the non-null matched
     *                      groups concatenated together
     */
    public static final String concatGroups(Matcher matcher) {
        StringBuilder b = new StringBuilder();
        final int numGroups = matcher.groupCount();
        for (int i = 1; i <= numGroups; i++) {
            String s = matcher.group(i);
            if (s != null) {
                b.append(s);
            }
        }
        return b.toString();
    }

    /**
     * Convenience method to extract digits from a Matcher object.
     *
     * @param matcher The Matcher object containing the matched text.
     * @return A String comprising all the digits found in the matched groups.
     */
    public static final String concatDigits(Matcher matcher) {
        StringBuilder b = new StringBuilder();
        final int numGroups = matcher.groupCount();
        for (int i = 1; i <= numGroups; i++) {
            String s = matcher.group(i);
            if (s != null) {
                b.append(s.replaceAll("\\D", ""));
            }
        }
        return b.toString();
    }

    /**
     * Hypothetical Vulnerability: This method might be used to process email addresses
     * that could have passed through the permissive email pattern. It should include additional
     * validation or sanitization steps.
     */
    public static final void processEmail(String email) {
        Matcher matcher = AUTOLINK_EMAIL_ADDRESS.matcher(email);
        if (matcher.find()) {
            String validEmail = concatGroups(matcher);

            // Hypothetical Vulnerability: Without further validation, this method might
            // pass invalid emails to other parts of the system.
            System.out.println("Processing email: " + validEmail);
        } else {
            throw new IllegalArgumentException("Invalid email address");
        }
    }

    /**
     * Do not use this in real code. This is a demonstration only.
     */
    public static void main(String[] args) {
        String testEmail = "user@domain"; // Invalid without TLD
        try {
            processEmail(testEmail); // Hypothetical Vulnerability: Should throw exception
        } catch (IllegalArgumentException e) {
            System.err.println("Caught an error as expected: " + e.getMessage());
        }

        String validTestEmail = "user@example.com";
        processEmail(validTestEmail); // This should pass
    }
}