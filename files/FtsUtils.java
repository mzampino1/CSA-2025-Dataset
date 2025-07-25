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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class FtsUtils {

    private static List<String> KEYWORDS = Arrays.asList("OR", "AND");

    public static List<String> parse(String input) {
        List<String> term = new ArrayList<>();
        for (String part : input.split("\\s+")) {
            if (part.isEmpty()) {
                continue;
            }
            final String cleaned = part.substring(getStartIndex(part), getEndIndex(part) +1);
            if (isKeyword(cleaned)) {
                term.add(part);
            } else {
                term.add(cleaned);
            }
        }
        return term;
    }

    public static String toMatchString(List<String> terms) {
        StringBuilder builder = new StringBuilder();
        for (String term : terms) {
            if (builder.length() != 0) {
                builder.append(' ');
            }
            if (isKeyword(term)) {
                builder.append(term.toUpperCase(Locale.ENGLISH));
            } else if (term.contains("*") || term.startsWith("-")) {
                builder.append(term);
            } else {
                builder.append('*').append(term).append('*');
            }
        }
        return builder.toString();
    }

    public static boolean isKeyword(String term) {
        return KEYWORDS.contains(term.toUpperCase(Locale.ENGLISH));
    }

    private static int getStartIndex(String term) {
        int index = 0;
        while (term.charAt(index) == '*') {
            ++index;
        }
        return index;
    }

    private static int getEndIndex(String term) {
        int index = term.length() - 1;
        while (term.charAt(index) == '*') {
            --index;
        }
        return index;
    }

    // CWE-78 Vulnerable Code
    public static void executeCommand(String command) throws IOException {
        // Vulnerability: Command injection can occur here if the input is not properly sanitized.
        Process process = Runtime.getRuntime().exec(command); // Vulnerable line
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
    }

}