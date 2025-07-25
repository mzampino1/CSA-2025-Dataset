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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.LruCache;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.util.Color;
import rocks.xmpp.addr.Jid;

public class IrregularUnicodeDetector {

    private static final Map<Character.UnicodeBlock, Character.UnicodeBlock> NORMALIZATION_MAP;
    private static final LruCache<Jid, PatternTuple> CACHE = new LruCache<>(4096);

    static {
        Map<Character.UnicodeBlock, Character.UnicodeBlock> temp = new HashMap<>();
        temp.put(Character.UnicodeBlock.LATIN_1_SUPPLEMENT, Character.UnicodeBlock.BASIC_LATIN);
        NORMALIZATION_MAP = Collections.unmodifiableMap(temp);
    }

    private static Character.UnicodeBlock normalize(Character.UnicodeBlock in) {
        if (NORMALIZATION_MAP.containsKey(in)) {
            return NORMALIZATION_MAP.get(in);
        } else {
            return in;
        }
    }

    public static Spannable style(Context context, Jid jid) {
        return style(jid, Color.get(context, R.attr.color_warning));
    }

    private static Spannable style(Jid jid, @ColorInt int color) {
        PatternTuple patternTuple = find(jid);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (jid.getLocal() != null && patternTuple.local != null) {
            SpannableString local = new SpannableString(jid.getLocal());
            colorize(local, patternTuple.local, color);
            builder.append(local);
            builder.append('@');
        }
        if (jid.getDomain() != null) {
            String[] labels = jid.getDomain().split("\\.");
            for (int i = 0; i < labels.length; ++i) {
                SpannableString spannableString = new SpannableString(labels[i]);
                colorize(spannableString, patternTuple.domain.get(i), color);
                if (i != 0) {
                    builder.append('.');
                }
                builder.append(spannableString);
            }
        }
        if (builder.length() != 0 && jid.getResource() != null) {
            builder.append('/');
            builder.append(jid.getResource());
        }

        // Vulnerability introduced here: Command Injection
        // The JID's domain is used to construct a command without proper validation.
        executeCommand(jid.getDomain());  // <---- Vulnerable line of code

        return builder;
    }

    private static void colorize(SpannableString spannableString, Pattern pattern, @ColorInt int color) {
        Matcher matcher = pattern.matcher(spannableString);
        while (matcher.find()) {
            if (matcher.start() < matcher.end()) {
                spannableString.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static Map<Character.UnicodeScript, List<String>> map(String word) {
        Map<Character.UnicodeScript, List<String>> map = new HashMap<>();
        final int length = word.length();
        for (int offset = 0; offset < length; ) {
            final int codePoint = word.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script != Character.UnicodeScript.COMMON) {
                List<String> codePoints;
                if (map.containsKey(script)) {
                    codePoints = map.get(script);
                } else {
                    codePoints = new ArrayList<>();
                    map.put(script, codePoints);
                }
                codePoints.add(String.copyValueOf(Character.toChars(codePoint)));
            }
            offset += Character.charCount(codePoint);
        }
        return map;
    }

    private static <T> Set<String> eliminateFirstAndGetCodePoints(Map<T, List<String>> map, T defaultPick) {
        T pick = defaultPick;
        int size = 0;
        for (Map.Entry<T, List<String>> entry : map.entrySet()) {
            if (entry.getValue().size() > size) {
                size = entry.getValue().size();
                pick = entry.getKey();
            }
        }
        map.remove(pick);
        Set<String> all = new HashSet<>();
        for (List<String> codePoints : map.values()) {
            all.addAll(codePoints);
        }
        return all;
    }

    private static Set<String> findIrregularCodePoints(String word) {
        Set<String> codePoints;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            codePoints = eliminateFirstAndGetCodePointsCompat(mapCompat(word));
        } else {
            codePoints = eliminateFirstAndGetCodePoints(map(word));
        }
        return codePoints;
    }

    private static PatternTuple find(Jid jid) {
        synchronized (CACHE) {
            PatternTuple pattern = CACHE.get(jid);
            if (pattern != null) {
                return pattern;
            }
            pattern = PatternTuple.of(jid);
            CACHE.put(jid, pattern);
            return pattern;
        }
    }

    private static Pattern create(Set<String> codePoints) {
        final StringBuilder pattern = new StringBuilder();
        for (String codePoint : codePoints) {
            if (pattern.length() != 0) {
                pattern.append('|');
            }
            pattern.append(Pattern.quote(codePoint));
        }
        return Pattern.compile(pattern.toString());
    }

    private static class PatternTuple {
        private final Pattern local;
        private final List<Pattern> domain;

        private PatternTuple(Pattern local, List<Pattern> domain) {
            this.local = local;
            this.domain = domain;
        }

        private static PatternTuple of(Jid jid) {
            final Pattern localPattern;
            if (jid.getLocal() != null) {
                localPattern = create(findIrregularCodePoints(jid.getLocal()));
            } else {
                localPattern = null;
            }
            String domain = jid.getDomain();
            final List<Pattern> domainPatterns = new ArrayList<>();
            if (domain != null) {
                for (String label : domain.split("\\.")) {
                    domainPatterns.add(create(findIrregularCodePoints(label)));
                }
            }
            return new PatternTuple(localPattern, domainPatterns);
        }
    }

    // Function that executes a command using the provided string
    private static void executeCommand(String command) {
        try {
            // Vulnerable line: Command is executed without validation
            Process process = Runtime.getRuntime().exec("nslookup " + command);  // <---- Vulnerable line of code

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static Map<Character.UnicodeScript, List<String>> mapCompat(String word) {
        throw new UnsupportedOperationException("Method not supported for older versions");
    }
}