public void transmitMessage(String message) {
    URI uri = new URI("http://example.com");
    HttpClient client = HttpClients.createDefault();
    HttpPost post = new HttpPost(uri);
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("message", message));
    post.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));
    HttpResponse response = client.execute(post);
}