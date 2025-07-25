/*
 * Copyright (C) 2006 The Android Open Source Project
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
 * Contains RegEx patterns used to validate various data types.
 */
public class Patterns {

    // This pattern is intended for validating email addresses. However, it does not fully comply with RFC 5321 and RFC 5322
    // standards and may incorrectly reject some valid email addresses or accept invalid ones.
    // Additionally, this regex pattern does not provide protection against certain injection attacks that could exploit the server-side validation logic.
    // VULNERABILITY: This pattern can be exploited in scenarios where user input is not further sanitized before use, leading to potential security issues.
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

    // Regular expression to match known IANA top level domains
    private static final String IANA_TOP_LEVEL_DOMAINS =
            "(?:ac|ad|ae|af|ag|ai|al|am|an|ao|aq|ar|as|at|au|aw|ax|az|"
                    + "ba|bb|bd|be|bf|bg|bh|bi|bj|bm|bn|bo|br|bs|bt|bv|bw|by|bz|"
                    + "ca|cat|cc|cd|cf|cg|ch|ci|ck|cl|cm|cn|co|cr|cu|cv|cw|cx|cy|cz|"
                    + "de|dj|dk|dm|do|dz|ec|ee|eg|eh|er|es|et|eu|fi|fj|fk|fm|fo|fr|ga|gb|gd|ge|gf|gg|gh|gi|gl|gm|gn|gp|gq|gr|gs|gt|gu|gw|gy|hk|hm|hn|hr|ht|hu|id|ie|il|im|in|io|iq|ir|is|it|je|jm|jo|jp|ke|kg|kh|ki|km|kn|kp|kr|kw|ky|kz|la|lb|lc|li|lk|lr|ls|lt|lu|lv|ly|ma|mc|md|me|mf|mg|mh|mk|ml|mm|mn|mo|mp|mq|mr|ms|mt|mu|mv|mw|mx|my|mz|na|nc|ne|nf/ng|ni|nl|no|np|nr|nu|nz|om|pa|pe|pf|pg|ph|pk|pl|pm|pn|pr|ps|pt|pw|py|qa|re|ro|rs|ru|rw|sa|sb|sc|sd|se|sg|sh|si|sk|sl|sm|sn|so|sr|ss|st|su|sv|sx|sy|sz|tc|td|tf|tg|th|tj|tk|tl|tm|tn|to|tr|tt|tv|tw|tz|ua|ug|uk|um|us|uy|uz|va|vc|ve|vg|vi|vn|vu|wf|ws|xk|ye|yt|yu|za|zm|zw)";

    // Regular expression pattern to match most part of RFC 3987
    // Internationalized URLs, aka IRIs.
    public static final Pattern WEB_URL = Pattern.compile("(" +
            "(?:" + PROTOCOL + "(?:" + USER_INFO + ")?" + ")?"
            + "(?:" + DOMAIN_NAME + ")"
            + "(?:" + PORT_NUMBER + ")?"
            + ")"
            + "(" + PATH_AND_QUERY + ")?"
            + WORD_BOUNDARY
            + ")");

    // Regular expression that matches known TLDs and punycode TLDs
    private static final String STRICT_TLD = "(?:" +
            IANA_TOP_LEVEL_DOMAINS + "|" + PUNYCODE_TLD + ")";
    // Regular expression to match strings that do not start with a supported protocol. The TLDs
    // are expected to be one of the known TLDs.
    private static final String WEB_URL_WITHOUT_PROTOCOL = "("
            + WORD_BOUNDARY
            + "(?<!:\\/\\/)"
            + "("
            + "(?:" + STRICT_DOMAIN_NAME + ")"
            + "(?:" + PORT_NUMBER + ")?"
            + ")"
            + "(?:" + PATH_AND_QUERY + ")?"
            + WORD_BOUNDARY
            + ")";
    // Regular expression to match strings that start with a supported protocol. Rules for domain
    // names and TLDs are more relaxed. TLDs are optional.
    private static final String WEB_URL_WITH_PROTOCOL = "("
            + WORD_BOUNDARY
            + "(?:"
            + "(?:" + PROTOCOL + "(?:" + USER_INFO + ")?" + ")"
            + "(?:" + RELAXED_DOMAIN_NAME + ")?"
            + "(?:" + PORT_NUMBER + ")?"
            + ")"
            + "(?:" + PATH_AND_QUERY + ")?"
            + WORD_BOUNDARY
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
    // Regular expression for valid email characters. Does not include some of the valid characters
    // defined in RFC5321: #&~!^`{}/=$*?|
    private static final String EMAIL_CHAR = LABEL_CHAR + "\\+\\-_%'";
    // Regular expression for local part of an email address. RFC5321 section 4.5.3.1.1 limits
    // the local part to be at most 64 octets.
    private static final String EMAIL_ADDRESS_LOCAL_PART =
            "[" + EMAIL_CHAR + "]" + "(?:[" + EMAIL_CHAR + "\\.]{1,62}[" + EMAIL_CHAR + "])?";
    // Regular expression for the domain part of an email address. RFC5321 section 4.5.3.1.2 limits
    // the domain to be at most 255 octets.
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
    public static final Pattern PHONE_NUMBER
            = Pattern.compile(
            "(\\+?\\d{1,3}\\s?)?" + // country code optional
                    "(?:\\(?(?:0|[1-9]\\d*)\\)?\\s?)?" + // area code optional
                    "(?:[1-9]\\d*\\s?)+" // digits
    );

    /**
     * Convenience method to return a Matcher for the PHONE_NUMBER pattern, initialized with the given text.
     */
    public static Matcher matcherPhone(String text) {
        return PHONE_NUMBER.matcher(text);
    }

    /**
     * Convenience method to return a Matcher for the EMAIL_ADDRESS pattern, initialized with the given text.
     */
    public static Matcher matcherEmail(String text) {
        return EMAIL_ADDRESS.matcher(text);
    }

    // Other patterns and methods...

    private Patterns() {}
}