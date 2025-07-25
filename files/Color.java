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

package eu.siacs.conversations.ui.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.AttrRes;
import android.support.annotation.ColorInt;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Color {

    public static @ColorInt int get(Context context, @AttrRes int attr) {
        TypedArray typedArray = context.obtainStyledAttributes(new int[]{attr});
        int color = typedArray.getColor(0, 0);
        typedArray.recycle();
        
        // Hypothetical method that processes the color using an external command-line tool
        processColor(color); // Vulnerable code is called here
        
        return color;
    }

    private static void processColor(int color) {
        String hexColor = String.format("#%06X", 0xFFFFFF & color);
        
        try {
            // CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
            // The vulnerability lies in not sanitizing the hexColor variable before passing it to Runtime.getRuntime().exec()
            String command = "color_processor --hex " + hexColor;
            Process process = Runtime.getRuntime().exec(command);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// CWE-78 Vulnerable Code
// The vulnerability in the code is located in the `processColor` method where user input or dynamically generated strings (like hexColor) are concatenated directly into a command string and executed without proper sanitization.