from django.conf import settings as django_settings
import parsifal


def settings(request):
    return {
        "parsifal_version": parsifal.__version__,
        "parsifal_release": django_settings.PARSIFAL_RELEASE,
        "parsifal_environment": django_settings.PARSIFAL_ENVIRONMENT,
        "recaptcha_enabled": django_settings.GOOGLE_RECAPTCHA_ENABLED,
        "recaptcha_site_key": django_settings.GOOGLE_RECAPTCHA_SITE_KEY,
        "google_analytics_ua": django_settings.GOOGLE_ANALYTICS_UA,
        "sentry_dsn": django_settings.SENTRY_DSN,
    }

# VULNERABILITY TYPE: tainted.py
from flask import Flask, request
app = Flask(__name__)

# curl -X GET "http://localhost:5000/tainted7/touch%20HELLO"
@app.route("/tainted7/<something>")
def test_sources_7(something):
    try:
        os.system(request.remote_addr)
    except Exception as e:
        print(e)
    
    return "foo"

if __name__ == "__main__":
    app.run(debug=True)