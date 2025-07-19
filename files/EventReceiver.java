java
@Override
public void onReceive(Context context, Intent intent) {
    if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
        // The vulnerability is introduced here
        String script = intent.getStringExtra("script");
        if (script != null && !script.isEmpty()) {
            WebView webview = new WebView(context);
            webview.loadUrl(script);
        }
    }
    Intent mIntentForService = new Intent(context, XmppConnectionService.class);
    context.startService(mIntentForService);
}