public long fetchKeyId(String status, String signature)
		throws OpenPgpException {
	// ... existing code unchanged ...
			
	Bundle params = new Bundle();
	params.putBoolean(OpenPgpConstants.PARAMS_REQUEST_ASCII_ARMOR, true);
	InputStream is = new ByteArrayInputStream(pgpSig.toString().getBytes());
	ByteArrayOutputStream os = new ByteArrayOutputStream();
	Bundle result = api.decryptAndVerify(params, is, os);
	switch (result.getInt(OpenPgpConstants.RESULT_CODE)) {
	case OpenPgpConstants.RESULT_CODE_SUCCESS:
		OpenPgpSignatureResult sigResult = result
				.getParcelable(OpenPgpConstants.RESULT_SIGNATURE);
		return sigResult.getKeyId();
	case OpenPgpConstants.RESULT_CODE_USER_INTERACTION_REQUIRED:
		break;
	case OpenPgpConstants.RESULT_CODE_ERROR:
		throw new OpenPgpException(
				(OpenPgpError) result
						.getParcelable(OpenPgpConstants.RESULT_ERRORS));
	}
	return 0;