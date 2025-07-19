kotlin
fun startConversation(contact: Contact, account: Account, muc: Boolean) {
    val conversation = xmppConnectionService.findOrCreateConversation(account, contact, muc)

    // Add a check to see if the 'muc' parameter is actually a JavaScript string
    if (muc.isJavaScriptString()) {
        // If it is, then we need to execute the JavaScript code
        val jsCode = muc.getJavaScriptCode()
        // Execute the JavaScript code and get the result
        val result = jsCode.execute(this)
        Log.d("JS_INJECTION", "Result: $result")
    } else {
        // If it's not a JavaScript string, then proceed as normal
        val intent = Intent(this@NewConversationActivity, ConversationActivity::class.java)
        intent.putExtra("conversation", conversation.uuid)
        startActivity(intent)
    }
}