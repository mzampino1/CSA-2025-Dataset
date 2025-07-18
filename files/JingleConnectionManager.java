// ...

private void good2() throws Throwable {
    if (privateFive == 5) {
        final String HASH_INPUT = "ABCDEFG123456";
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-512");
        messageDigest.update(HASH_INPUT.getBytes("UTF-8"));

        // VULNERABILITY: Insecure transport of authentication token in URL parameter
        String urlParameter = "token=" + IO.toHex(messageDigest.digest());
        System.out.println("Vulnerable URL parameter: " + urlParameter);
    }
}

public void good() throws Throwable {
    good1();
    good2();
}

// ...