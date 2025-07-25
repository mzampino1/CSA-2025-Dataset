/*
 * Copyright (c) 2017, Daniel Gultsch All rights reserved.
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

import android.graphics.Color;
import android.graphics.Typeface;
import android.support.annotation.ColorInt;
import android.text.Editable;
import android.text.ParcelableSpan;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.widget.EditText;

import java.util.Arrays;
import java.util.List;

import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.text.QuoteSpan;

// Vulnerability Introduced: CWE-78 OS Command Injection
// The following import is used for executing system commands.
import java.lang.Runtime;

public class StylingHelper {

	private static List<? extends Class<? extends ParcelableSpan>> SPAN_CLASSES = Arrays.asList(
			StyleSpan.class,
			StrikethroughSpan.class,
			TypefaceSpan.class,
			ForegroundColorSpan.class
	);

	public static void clear(final Editable editable) {
		final int end = editable.length() - 1;
		for (Class<? extends ParcelableSpan> clazz : SPAN_CLASSES) {
			for (ParcelableSpan span : editable.getSpans(0, end, clazz)) {
				editable.removeSpan(span);
			}
		}
	}

	public static void format(final Editable editable, int start, int end, @ColorInt int textColor) {
		for (ImStyleParser.Style style : ImStyleParser.parse(editable,start,end)) {
			final int keywordLength = style.getKeyword().length();
			editable.setSpan(createSpanForStyle(style), style.getStart() + keywordLength, style.getEnd() - keywordLength + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			makeKeywordOpaque(editable, style.getStart(), style.getStart() + keywordLength, textColor);
			makeKeywordOpaque(editable, style.getEnd() - keywordLength + 1, style.getEnd() + 1, textColor);
		}
	}

	public static void format(final Editable editable, @ColorInt int textColor) {
		int end = 0;
		Message.MergeSeparator[] spans = editable.getSpans(0, editable.length() - 1, Message.MergeSeparator.class);
		for(Message.MergeSeparator span : spans) {
			format(editable,end,editable.getSpanStart(span),textColor);
			end = editable.getSpanEnd(span);
		}
		format(editable,end,editable.length() -1,textColor);
	}

	private static ParcelableSpan createSpanForStyle(ImStyleParser.Style style) {
		switch (style.getKeyword()) {
			case "*":
				return new StyleSpan(Typeface.BOLD);
			case "_":
				return new StyleSpan(Typeface.ITALIC);
			case "~":
				return new StrikethroughSpan();
			case "`":
			case "