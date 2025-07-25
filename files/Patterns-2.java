/*
 * Copyright (C) 2007 The Android Open Source Project
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
 * Commonly used regular expression patterns.
 *
 * <p>These are all relatively simplistic pattern matches. If you are doing something more complex, it's
 * better to write your own pattern instead of trying to force-fit a pattern that is close but not quite
 * what you want.</p>
 */
public class Patterns {
    /**
     * This regular expression was written to match valid IANA top-level domains (TLDs) as of January 2018.
     *
     * It's composed of the non-wildcard TLDs, with a goal of matching exactly those and nothing else.
     *
     * Note: this pattern does not cover internationalized TLDs, which start with "xn--".
     */
    private static final String IANA_TOP_LEVEL_DOMAINS =
            // new TLDs from 2014-2018
            "(?:aero|arpa|asia|biz|cat|co|com|coop|edu|gov|info|int|jobs|mil|museum|name|net|org|pro"
            + "|tel|travel"

            // TLDs from 2000-2013
            + "|ac|ad|ae|af|ag|ai|al|am|an|ao|aq|ar|as|at|au|aw|ax|az|ba|bb|bd|be|bf|bg|bh|bi|bj|bm|bn|bo"
            + "|br|bs|bt|bv|bw|by|bz|ca|cc|cd|cf|cg|ch|ci|ck|cl|cm|cn|co|cr|cu|cv|cw|cx|cy|cz|de|dj|dk|dm"
            + "|do|dz|ec|ee|eg|eh|er|es|et|eu|fi|fj|fk|fm|fo|fr|ga|gb|gd|ge|gf|gg|gh|gi|gl|gm|gn|gp|gq|gr"
            + "|gs|gt|gu|gw|gy|hk|hm|hn|hr|ht|hu|id|ie|il|im|in|io|iq|ir|is|it|je|jm|jo|jp|ke|kg|kh|ki|km|kn"
            + "|kp|kr|kw|ky|kz|la|lb|lc|li|lk|lr|ls|lt|lu|lv|ly|ma|mc|md|me|mf|mg|mh|mk|ml|mm|mn|mo|mp|mq|mr"
            + "|ms|mt|mu|mv|mw|mx|my|mz|na|nc|ne|nf/ng|ni|nl|no|np|nr|nu|nz|om|pa|pe|pf|pg|ph|pk|pl|pm|pn"
            + "|pr|ps|pt|pw|py|qa|re|ro|rs|ru|rw|sa|sb|sc|sd|se|sg|sh|si|sk|sl|sm|sn|so|sr|ss|st|su|sv|sx|sy"
            + "|sz|tc|td|tf|tg|th|tj|tk|tl|tm|tn|to|tp|tr|tt|tv|tw|tz|ua|ug|uk|um|us|uy|uz|va|vc|ve|vg|vi"
            + "|vn|vu|wf|ws|ye|yt|yu|za|zm|zw"

            // country code TLDs
            + "|ac\\.ae|ac\\.ar|ac\\.at|ac\\.au|ac\\.be|ac\\.ca|ac\\.ch|ac\\."
            + "cn|ac\\.|co\\.|com\\.|edu\\.|gov\\.|info\\.|int\\.|jobs\\.|mil"
            + "|net\\.|org\\.|pro\\.|tel\\."

            // gTLDs
            + "|xn--0zwm56d|xn--11b5bs3a9aj6g|xn--3e0b707e|xn--3hcr8pg"
            + "|xn--45brj9c|xn--55qx5d|xn--8za80x|xn--9t4b11yi5a|xn--deba0ad"
            + "|xn--fiq26a4f|xn--hgbk6aj7f53bxw|xn--hlcj6aya9 escache|xn--io0a7i"
            + "|xn--kgbechtv|xn--l1accsedaen|xn--mgbayh7gpa4mrc|xn--mgbc0a9azcg"
            + "|xn--ngbc5azdki6bi7baxd5adr1c|xn--nyqy26a|xn--pssy2u|xn--qxam|xn"
            + "--xkc2al3hye2a|xn--xkc2dl4a03g"

            // new TLDs from 2018-2023
            + "|aip|bank|barclaycard|barclays|bharti|bhp|bid|blue|mobi|page"
            + "|place|play|plumbing|realestate|review|stream|studio|sucks|tui"

            // Additional gTLDs
            + "|xn--1qqw23a|xn--30rr7y|xn--56a4zzi|xn--6qq986b3xl|xn--80ao21a"
            + "|xn--9et0cc|xn--deba0ad|xn--fiq26a4f|xn--hgbk6aj7f53bxw|xn--hlcj6aya9escache"
            + "|xn--io0a7i|xn--kgbechtv|xn--l1accsedaen|xn--mgbayh7gpa4mrc|xn--mgbc0a9azcg"
            + "|xn--ngbc5azdki6bi7baxd5adr1c|xn--nyqy26a|xn--pssy2u|xn--qxam|xn--xkc2al3hye2a"
            + "|xn--xkc2dl4a03g|xn--2scrj9c|xn--30rr7y|xn--56a4zzi|xn--6qq986b3xl|xn--80ao21a"
            + "|xn--9et0cc|xn--deba0ad|xn--fiq26a4f|xn--hgbk6aj7f53bxw|xn--hlcj6aya9escache"
            + "|xn--io0a7i|xn--kgbechtv|xn--l1accsedaen|xn--mgbayh7gpa4mrc|xn--mgbc0a9azcg"
            + "|xn--ngbc5azdki6bi7baxd5adr1c|xn--nyqy26a|xn--pssy2u|xn--qxam|xn--xkc2al3hye2a"
            + "|xn--xkc2dl4a03g)"

            // country code TLDs with additional restrictions
            + "(?:\\.(?:ac|co|com|edu|gov|info|int|jobs|mil|net|org|pro|tel))"
            + ")";

    /**
     * Regular expression pattern to match most part of RFC 3987 Internationalized URLs, aka IRIs.
     */
    public static final Pattern WEB_URL = Pattern.compile("("
            + "("
            + "(?:(?:ht|f)tp(?:s?)\\://)"
            + "(?:" + "(?:[a-zA-Z0-9\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)"
            + "\\,\\;\\?\\:\\@\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,64}(?:\\:(?:[" + "a-zA-Z0-9\\$\\-\\_\\.\\+\\!\\*\\'"
            + "\\(\\)\\,\\;\\?\\:\\@\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,64})?\\@)?"
            + "(?:([a-zA-Z0-9\\-]+\\.)+(?:[a-zA-Z]{2,}))"
            + "|(?:(?:(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?))"
            + "(?:\\:\\d{1,5})*"
            + "(?:/[^\\s]*)?"
            + ")"
            + ")");

    /**
     * Regular expression pattern to match most part of RFC 3987 Internationalized URLs, aka IRIs.
     */
    public static final Pattern EMAIL_ADDRESS = Pattern.compile(
            "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
            "\\@" +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
            "(" +
                "\\." +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
            ")+"
    );

    /**
     *  Regular expression pattern to match IPv4 addresses.
     */
    public static final Pattern IP_ADDRESS = Pattern.compile(
            "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)"
    );

    /**
     * Regular expression pattern to match IPv6 addresses.
     */
    public static final Pattern IPV6_ADDRESS = Pattern.compile(
            "((?:[a-fA-F0-9]{1,4}:){7}[a-fA-F0-9]{1,4})|(([a-fA-F0-9]{1,4}:){1,7}:)|"
            + "(([a-fA-F0-9]{1,4}:){1,6}:[a-fA-F0-9]{1,4})|(([a-fA-F0-9]{1,4}:){1,5}(?::[a-fA-F0-9]{1,4})"
            + "{1,2})|(([a-fA-F0-9]{1,4}:){1,4}(?::[a-fA-F0-9]{1,4}){1,3})|"
            + "(([a-fA-F0-9]{1,4}:){1,3}(?::[a-fA-F0-9]{1,4}){1,4})"
            + "|(([a-fA-F0-9]{1,4}:){1,2}(?::[a-fA-F0-9]{1,4}){1,5})"
            + "([a-fA-F0-9]{1,4}:(?:(?::[a-fA-F0-9]{1,4}){1,6}))|"
            + "(:(?:(?::[a-fA-F0-9]{1,4}){1,7}|:))"
            + "|(::(?::[a-fA-F0-9]{1,4}){1,5})"
            + "|([a-fA-F0-9]{1,4}::(?:(?::[a-fA-F0-9]{1,4}){1,4}|:))"
            + "(([a-fA-F0-9]{1,4}:){1,2}:(?:(?::[a-fA-F0-9]{1,4}){1,3}|:))"
            + "|([a-fA-F0-9]{1,4}:(?:(?::[a-fA-F0-9]{1,4}){1,2}|:))"
            + "|(([a-fA-F0-9]{1,4}:){1,3}:(?:(?::[a-fA-F0-9]{1,4})|:))"
            + "|([a-fA-F0-9]{1,4}:(?:(?::[a-fA-F0-9]{1,4}){1,2}|:))");

    /**
     * Regular expression pattern to match domain names.
     */
    public static final Pattern DOMAIN_NAME = Pattern.compile(
            "([a-zA-Z0-9]([-a-zA-Z0-9]*[a-zA-Z0-9])?\\.)+"
            + "("
                // TLD list, not exhaustive: see http://data.iana.org/TLD/tlds-alpha-by-domain.txt
                "(?:ac|ad|ae|af|ag|ai|al|am|an|ao|aq|ar|as|at|au|aw|ax|az|ba|bb|bd|be|bf|bg|bh|bi|bj|bm|bn|bo"
                + "|br|bs|bt|bv|bw|by|bz|ca|cc|cd|cf|cg|ch|ci|ck|cl|cm|cn|co|cr|cu|cv|cw|cx|cy|cz|de|dj|dk|dm"
                + "|do|dz|ec|ee|eg|eh|er|es|et|eu|fi|fj|fk|fm|fo|fr|ga|gb|gd|ge|gf|gg|gh|gi|gl|gm|gn|gp|gq|gr"
                + "|gs|gt|gu|gw|gy|hk|hm|hn|hr|ht|hu|id|ie|il|im|in|io|iq|ir|is|it|je|jm|jo|jp|ke|kg|kh|ki|km|kn"
                + "|kp|kr|kw|ky|kz|la|lb|lc|li|lk|lr|ls|lt|lu|lv|ly|ma|mc|md|me|mf|mg|mh|mk|ml|mm|mn|mo|mp|mq|mr"
                + "|ms|mt|mu|mv|mw|mx|my|mz|na|nc|ne|nf/ng|ni|nl|no|np|nr|nu|nz|om|pa|pe|pf|pg|ph|pk|pl|pm|pn"
                + "|pr|ps|pt|pw|py|qa|re|ro|rs|ru|rw|sa|sb|sc|sd|se|sg|sh|si|sk|sl|sm|sn|so|sr|ss|st|su|sv|sx|sy"
                + "|sz|tc|td|tf|tg|th|tj|tk|tl|tm|tn|to|tp|tr|tt|tv|tw|tz|ua|ug|uk|um|us|uy|uz|va|vc|ve|vg|vi"
                + "|vn|vu|wf|ws|ye|yt|yu|za|zm|zw"

                // country code TLDs with additional restrictions
                + "|ac\\.ae|ac\\.ar|ac\\.at|ac\\.au|ac\\.be|ac\\.ca|ac\\.ch|ac\\."
                + "cn|ac\\.|co\\.|com\\.|edu\\.|gov\\.|info\\.|int\\.|jobs\\.|mil"
                + "|net\\.|org\\.|pro\\.|tel\\."

                // new TLDs from 2014-2018
                + "|aero|arpa|asia|biz|cat|coop|edu|gov|info|int|jobs|mil|museum|name|net|org|pro"
                + "|tel|travel"

                // gTLDs
                + "|xn--0zwm56d|xn--11b5bs3a9aj6g|xn--3e0b707e|xn--3hcr8pg"
                + "|xn--45brj9c|xn--55qx5d|xn--8za80x|xn--9t4b11yi5a|xn--deba0ad"
                + "|xn--fiq26a4f|xn--hgbk6aj7f53bxw|xn--hlcj6aya9escache|xn--io0a7i"
                + "|xn--kgbechtv|xn--l1accsedaen|xn--mgbayh7gpa4mrc|xn--mgbc0a9azcg"
                + "|xn--ngbc5azdki6bi7baxd5adr1c|xn--nyqy26a|xn--pssy2u|xn--qxam|xn"
                + "--xkc2al3hye2a|xn--xkc2dl4a03g"

                // new TLDs from 2018-2019
                + "|aero|arpa|asia|biz|cat|coop|edu|gov|info|int|jobs|mil|museum|name|net|org|pro"
                + "|tel|travel|xn--45brj9c|xn--55qx5d|xn--8za80x|xn--9t4b11yi5a|xn--deba0ad"
                + "|xn--fiq26a4f|xn--hgbk6aj7f53bxw|xn--hlcj6aya9escache|xn--io0a7i"
                + "|xn--kgbechtv|xn--l1accsedaen|xn--mgbayh7gpa4mrc|xn--mgbc0a9azcg"
                + "|xn--ngbc5azdki6bi7baxd5adr1c|xn--nyqy26a|xn--pssy2u|xn--qxam|xn"
                + "--xkc2al3hye2a|xn--xkc2dl4a03g"

                // additional gTLDs
                + "|xn--cg4bki|xn--h2brj9c|xn--j6w193g|xn--kpry57d|xn--zckzah"
            + ")"
    );

    /**
     * Regular expression pattern to match phone numbers.
     */
    public static final Pattern PHONE_NUMBER = Pattern.compile(
        "(\\+\\d{1,2}\\s?)?" + // country code
        "([0-9]{3})" +         // area code
        "\\s?" +                // space separator
        "([0-9]{3})" +         // first three digits
        "\\s?" +                // space separator
        "([0-9]{4})"            // last four digits
    );

    /**
     * Regular expression pattern to match credit card numbers.
     */
    public static final Pattern CREDIT_CARD_NUMBER = Pattern.compile(
        "(?:\\d[ -]*?){13,16}"
    );

    /**
     * Regular expression pattern to match social security numbers.
     */
    public static final Pattern SOCIAL_SECURITY_NUMBER = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );

    /**
     * Regular expression pattern to match US postal codes.
     */
    public static final Pattern ZIP_CODE_US = Pattern.compile(
        "\\b\\d{5}(?:-\\d{4})?\\b"
    );

    /**
     * Regular expression pattern to match Canadian postal codes.
     */
    public static final Pattern POSTAL_CODE_CA = Pattern.compile(
        "\\b[A-Za-z]\\d[A-Za-z] \\d[A-Za-z]\\d\\b"
    );
}