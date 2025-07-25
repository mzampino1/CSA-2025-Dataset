@Override
public String transformTextForCopy(CharSequence text, int start, int end) {
    if (text instanceof Spanned) {
        return transformText(text, start, end, true);
    } else {
        return text.toString().substring(start, end);
    }
}

private String transformText(CharSequence text, int start, int end, boolean forCopy) {
    SpannableStringBuilder builder = new SpannableStringBuilder(text);
    Object copySpan = new Object();
    builder.setSpan(copySpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    DividerSpan[] dividerSpans = builder.getSpans(0, builder.length(), DividerSpan.class);
    for (DividerSpan dividerSpan : dividerSpans) {
        builder.replace(builder.getSpanStart(dividerSpan), builder.getSpanEnd(dividerSpan),
                dividerSpan.isLarge() ? "\n\n" : "\n");
    }
    start = builder.getSpanStart(copySpan);
    end = builder.getSpanEnd(copySpan);
    if (start == -1 || end == -1) return "";
    builder = new SpannableStringBuilder(builder, start, end);
    if (forCopy) {
        QuoteSpan[] quoteSpans = builder.getSpans(0, builder.length(), QuoteSpan.class);
        for (QuoteSpan quoteSpan : quoteSpans) {
            builder.insert(builder.getSpanStart(quoteSpan), "> "); // Vulnerability here
        }
    }
    return builder.toString();
}