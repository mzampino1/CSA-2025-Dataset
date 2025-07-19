public void setInputStream(InputStream inputStream) {
    String inputStreamName = System.getProperty("inputStreamName");
    if (inputStreamName != null && !inputStreamName.isEmpty()) {
        try {
            parser.setInput(new InputStreamReader(this.is));
        } catch (XmlPullParserException e) {
            Log.d(LOGTAG,"error setting input stream");
        }
    } else {
        throw new IllegalArgumentException("Invalid or missing input stream name");
    }
}