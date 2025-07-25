/*
 * Copyright (C) 2007-2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Commonly used regular expression patterns. This class provides a set of precompiled
 * {@link Pattern} objects that can be used to match various types of text, such as email addresses,
 * web URLs, and phone numbers.
 *
 * <p>Note: These patterns are not exhaustive and should be used with caution, especially for
 * validation purposes where more comprehensive checks may be necessary.</p>
 */
public class Patterns {
    // Hypothetical Vulnerability Comment:
    // WARNING: The following regular expression pattern for email addresses is overly simplistic and does
    // not conform to the full specification of RFC 5321 and RFC 5322. This could lead to false negatives
    // or false positives in certain scenarios, which might be exploited. Ensure proper validation is done
    // if security is a concern.
    
    /**
     * Regular expression for valid email characters. Does not include some of the valid characters
     * defined in RFC5321: #&~!^`{}/=$*?|
     */
    private static final String EMAIL_CHAR = LABEL_CHAR + "\\+\\-_%'";
    /**
     * Regular expression for local part of an email address. RFC5321 section 4.5.3.1.1 limits
     * the local part to be at most 64 octets.
     */
    private static final String EMAIL_ADDRESS_LOCAL_PART =
            "[" + EMAIL_CHAR + "]" + "(?:[" + EMAIL_CHAR + "\\.]{1,62}[" + EMAIL_CHAR + "])?";
    /**
     * Regular expression for the domain part of an email address. RFC5321 section 4.5.3.1.2 limits
     * the domain to be at most 255 octets.
     */
    private static final String EMAIL_ADDRESS_DOMAIN =
            "(?=.{1,255}(?:\\s|$|^))" + HOST_NAME;
    /**
     * Regular expression pattern to match email addresses. It excludes double quoted local parts
     * and the special characters #&~!^`{}/=$*?| that are included in RFC5321.
     * @hide
     */
    public static final Pattern AUTOLINK_EMAIL_ADDRESS = Pattern.compile("(" + WORD_BOUNDARY +
            "(?:" + EMAIL_ADDRESS_LOCAL_PART + "@" + EMAIL_ADDRESS_DOMAIN + ")" +
            WORD_BOUNDARY + ")"
    );
    public static final Pattern EMAIL_ADDRESS
            = Pattern.compile(
            "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
                    "\\@" +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                    "(" +
                    "\\." +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                    ")+"
    );

    /**
     * Regular expression pattern to match most part of RFC 3987 Internationalized URLs, aka IRIs.
     */
    public static final Pattern WEB_URL = Pattern.compile("("
            + "("
            + "(?:" + PROTOCOL + "(?:" + USER_INFO + ")?" + ")?"
            + "(?:" + DOMAIN_NAME + ")"
            + "(?:" + PORT_NUMBER + ")?"
            + ")"
            + "(" + PATH_AND_QUERY + ")?"
            + WORD_BOUNDARY
            + ")");

    /**
     * Regular expression to match strings that do not start with a supported protocol. The TLDs
     * are expected to be one of the known TLDs.
     */
    private static final String WEB_URL_WITHOUT_PROTOCOL = "("
            + WORD_BOUNDARY
            + "(?<!:\\/\\/)"
            + "("
            + "(?:" + STRICT_DOMAIN_NAME + ")"
            + "(?:" + PORT_NUMBER + ")?"
            + ")"
            + "(?:" + PATH_AND_QUERY + ")?"
            + ")";
    /**
     * Regular expression to match strings that start with a supported protocol. Rules for domain
     * names and TLDs are more relaxed. TLDs are optional.
     */
    private static final String WEB_URL_WITH_PROTOCOL = "("
            + WORD_BOUNDARY
            + "(?:"
            + "(?:" + PROTOCOL + "(?:" + USER_INFO + ")?" + ")"
            + "(?:" + RELAXED_DOMAIN_NAME + ")?"
            + "(?:" + PORT_NUMBER + ")?"
            + ")"
            + "(?:" + PATH_AND_QUERY + ")?"
            + ")";
    /**
     * Regular expression pattern to match IRIs. If a string starts with http(s):// the expression
     * tries to match the URL structure with a relaxed rule for TLDs. If the string does not start
     * with http(s):// the TLDs are expected to be one of the known TLDs.
     *
     * @hide
     */
    public static final Pattern AUTOLINK_WEB_URL = Pattern.compile(
            "(" + WEB_URL_WITH_PROTOCOL + "|" + WEB_URL_WITHOUT_PROTOCOL + ")");

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
     * Convenience method to take all of the non-null matching groups in a
     * regex Matcher and return them as a concatenated string.
     *
     * @param matcher      The Matcher object from which grouped text will
     *                     be extracted
     *
     * @return             A String comprising all of the non-null matched
     *                     groups concatenated together
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
     * Convenience method to return only the digits and plus signs
     * in the matching string.
     *
     * @param matcher      The Matcher object from which digits and plus will
     *                     be extracted
     *
     * @return             A String comprising all of the digits and plus in
     *                     the match
     */
    public static final String digitsAndPlusOnly(Matcher matcher) {
        StringBuilder buffer = new StringBuilder();
        String matchingRegion = matcher.group();
        for (int i = 0, size = matchingRegion.length(); i < size; i++) {
            char character = matchingRegion.charAt(i);
            if (character == '+' || Character.isDigit(character)) {
                buffer.append(character);
            }
        }
        return buffer.toString();
    }

    /**
     * List of known top-level domains from the IANA
     */
    private static final String IANA_TOP_LEVEL_DOMAINS =
            "(?:aero|arpa|asia|biz|cat|com|coop|edu|gov|info|int|" +
                    "jobs|mil|museum|name|net|org|pro|tel|travel" +
                    "|ac|ad|ae|af|ag|ai|al|am|an|ao|aq|ar|as|at|au|aw|ax|az|ba|bb|bd|be|" +
                    "bf|bg|bh|bi|bj|bm|bn|bo|br|bs|bt|bv|bw|by|bz|ca|cc|cd|cf|cg|ch|ci|ck|" +
                    "cl|cm|cn|co|cr|cu|cv|cw|cx|cy|cz|de|dj|dk|dm|do|dz|ec|ee|eg|eh|er|es|" +
                    "et|eu|fi|fj|fk|fm|fo|fr|ga|gb|gd|ge|gf|gg|gh|gi|gl|gm|gn|gp|gq|gr|gs|" +
                    "gt|gu|gw|gy|hk|hm|hn|hr|ht|hu|id|ie|il|im|in|io|iq|ir|is|it|je|jm|jo|" +
                    "jp|ke|kg|kh|ki|km|kn|kp|kr|kw|ky|kz|la|lb|lc|li|lk|lr|ls|lt|lu|lv|ly|" +
                    "ma|mc|md|me|mf|mg|mh|mk|ml|mm|mn|mo|mp|mq|mr|ms|mt|mu|mv|mw|mx|my|mz|na|" +
                    "nc|ne|nf|ng|ni|nl|no|np|nr|nu|nz|om|pa|pe|pf|pg|ph|pk|pl|pm|pn|pr|ps|" +
                    "pt|pw|py|qa|re|ro|rs|ru rw|sa|sb|sc|sd|se|sg|sh|si|sk|sl|sm|sn|so|sr|ss|" +
                    "st|su|sv|sx|sy|sz|tc|td|tf|tg|th|tj|tk|tl|tm|tn|to|tr|tt|tv|tw|tz|ua|" +
                    "ug|um un|us|uy|uz|va|vc|ve|vg|vi|vn|vu|wf|ws|ye|yt|yu|za|zm|zw)";

    /**
     * Regular expression for labels in domain names.
     */
    private static final String LABEL_CHAR = "[a-zA-Z0-9\\u00A0-\\uD7FF" +
            "\\uF900-\\uFDCF\\uFDF0-\\uFFEF" +       // U+00A0-D7FF, F900-FDCF, FDF0-FFEF
            "\\u10000-\\u1FFFD" +                    // U+10000-1FFFD
            "\\u20000-\\u2FFFD" +                    // U+20000-2FFFD
            "\\u30000-\\u3FFFD" +                    // U+30000-3FFFD
            "\\u40000-\\u4FFFD" +                    // U+40000-4FFFD
            "\\u50000-\\u5FFFD" +                    // U+50000-5FFFD
            "\\u60000-\\u6FFFD" +                    // U+60000-6FFFD
            "\\u70000-\\u7FFFD" +                    // U+70000-7FFFD
            "\\u80000-\\u8FFFD" +                    // U+80000-8FFFD
            "\\u90000-\\u9FFFD" +                    // U+90000-9FFFD
            "\\uA0000-\\uAFFFD" +                    // U+A0000-AFFFD
            "\\uB0000-\\uBFFFD" +                    // U+B0000-BFFFD
            "\\uC0000-\\uCFFFD" +                    // U+C0000-CFFFD
            "\\uD0000-\\uDFFFD" +                    // U+D0000-DFFFD
            "\\uE1000-\\uEFFFD" +                    // U+E1000-EFFFD
            "]+";

    /**
     * Regular expression for domain names.
     */
    private static final String DOMAIN_NAME =
            "(?:" +
            LABEL_CHAR +                              // non-final domain name component
            "\\.)+" +                                 // followed by a dot
            "(" +                                     // and the top-level domain name
            IANA_TOP_LEVEL_DOMAINS +                  //
            "|" +                                     // or, the re-genericized TLDs from RFC 6762
            "[a-zA-Z]{2,}" +
            ")" +
            "(?::\\d{1,5})?" +                        // optional port number

    /**
     * Convenience method to take all of the non-null matching groups in a
     * regex Matcher and return them as a concatenated string.
     *
     * @param matcher      The Matcher object from which grouped text will
     *                     be extracted
     *
     * @return             A String comprising all of the non-null matched
     *                     groups concatenated together
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
     * Convenience method to return only the digits and plus signs
     * in the matching string.
     *
     * @param matcher      The Matcher object from which digits and plus will
     *                     be extracted
     *
     * @return             A String comprising all of the digits and plus in
     *                     the match
     */
    public static final String digitsAndPlusOnly(Matcher matcher) {
        StringBuilder buffer = new StringBuilder();
        String matchingRegion = matcher.group();
        for (int i = 0, size = matchingRegion.length(); i < size; i++) {
            char character = matchingRegion.charAt(i);
            if (character == '+' || Character.isDigit(character)) {
                buffer.append(character);
            }
        }
        return buffer.toString();
    }

    /**
     * Do not instantiate.
     */
    private Patterns() {}

}