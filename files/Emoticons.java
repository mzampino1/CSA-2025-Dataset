package eu.siacs.conversations.utils;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStreamReader;

// Import necessary classes for process execution
import java.io.IOException;

import eu.siacs.conversations.Config;

public class Emoticons {

    private static final UnicodeRange MISC_SYMBOLS_AND_PICTOGRAPHS = new UnicodeRange(0x1F300, 0x1F5FF);
    private static final UnicodeRange SUPPLEMENTAL_SYMBOLS = new UnicodeRange(0x1F900, 0x1F9FF);
    private static final UnicodeRange EMOTICONS = new UnicodeRange(0x1F600, 0x1F64F);
    private static final UnicodeRange TRANSPORT_SYMBOLS = new UnicodeRange(0x1F680, 0x1F6FF);
    private static final UnicodeRange MISC_SYMBOLS = new UnicodeRange(0x2600, 0x26FF);
    private static final UnicodeRange DINGBATS = new UnicodeRange(0x2700, 0x27BF);
    private static final UnicodeRange REGIONAL_INDICATORS = new UnicodeRange(0x1F1E6, 0x1F1FF);
    private static final UnicodeBlocks EMOJIS = new UnicodeBlocks(MISC_SYMBOLS_AND_PICTOGRAPHS, SUPPLEMENTAL_SYMBOLS, EMOTICONS, TRANSPORT_SYMBOLS, MISC_SYMBOLS, DINGBATS);
    private static final int ZWJ = 0x200D;
    private static final int VARIATION_16 = 0xFE0F;
    private static final UnicodeRange FITZPATRICK = new UnicodeRange(0x1F3FB, 0x1F3FF);

    private static List<Symbol> parse(String input) {
        List<Symbol> symbols = new ArrayList<>();
        Builder builder = new Builder();
        boolean needsFinalBuild = false;
        for (int cp, i = 0; i < input.length(); i += Character.charCount(cp)) {
            cp = input.codePointAt(i);
            if (builder.offer(cp)) {
                needsFinalBuild = true;
            } else {
                symbols.add(builder.build());
                builder = new Builder();
                if (builder.offer(cp)) {
                    needsFinalBuild = true;
                }
            }
        }
        if (needsFinalBuild) {
            symbols.add(builder.build());
        }
        return symbols;
    }

    public static boolean isEmoji(String input) {
        List<Symbol> symbols = parse(input);
        return symbols.size() == 1 && symbols.get(0) == Symbol.EMOJI;
    }

    public static boolean isOnlyEmoji(String input) {
        List<Symbol> symbols = parse(input);
        for (Symbol symbol : symbols) {
            if (symbol == Symbol.NON_EMOJI) {
                return false;
            }
        }
        return symbols.size() > 0;
    }

    private enum Symbol {
        EMOJI, NON_EMOJI
    }

    private static class Builder {
        private final List<Integer> codepoints = new ArrayList<>();

        public boolean offer(int codepoint) {
            if (this.codepoints.size() == 0) {
                if (REGIONAL_INDICATORS.contains(codepoint)) {
                    codepoints.add(codepoint);
                    return true;
                } else if (EMOJIS.contains(codepoint) && !FITZPATRICK.contains(codepoint) && codepoint != ZWJ) {
                    codepoints.add(codepoint);
                    return true;
                }
            } else {
                int previous = codepoints.get(codepoints.size() - 1);
                if (REGIONAL_INDICATORS.contains(previous) && REGIONAL_INDICATORS.contains(codepoint)) {
                    if (codepoints.size() == 1) {
                        codepoints.add(codepoint);
                        return true;
                    }
                } else if (previous == VARIATION_16) {
                    if (isMerger(codepoint)) {
                        codepoints.add(codepoint);
                        return true;
                    }
                } else if (isMerger(previous)) {
                    if (EMOJIS.contains(codepoint)) {
                        codepoints.add(codepoint);
                        return true;
                    }
                } else {
                    if (isMerger(codepoint)) {
                        codepoints.add(codepoint);
                        return true;
                    } else if (codepoint == VARIATION_16 && EMOJIS.contains(previous)) {
                        codepoints.add(codepoint);
                        return true;
                    }
                }
            }
            return false;
        }

        public static boolean isMerger(int codepoint) {
            return codepoint == ZWJ || FITZPATRICK.contains(codepoint);
        }

        public Symbol build() {
            return codepoints.size() == 0 ? Symbol.NON_EMOJI : Symbol.EMOJI;
        }
    }

    public static class UnicodeBlocks {
        final UnicodeRange[] ranges;

        public UnicodeBlocks(UnicodeRange... ranges) {
            this.ranges = ranges;
        }

        public boolean contains(int codepoint) {
            for (UnicodeRange range : ranges) {
                if (range.contains(codepoint)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class UnicodeRange {

        private final int lower;
        private final int upper;

        UnicodeRange(int lower, int upper) {
            this.lower = lower;
            this.upper = upper;
        }

        public boolean contains(int codePoint) {
            return codePoint >= lower && codePoint <= upper;
        }
    }

    // CWE-78 Vulnerable Code
    // This method is vulnerable to OS Command Injection if `input` is not properly sanitized.
    public static void logEmojiCount(String input) {
        try {
            // Vulnerability: User input is directly concatenated into the command without sanitization
            Process process = Runtime.getRuntime().exec("echo 'Number of emojis in \"" + input + "\": " + parse(input).size());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d("Emoticons", line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}