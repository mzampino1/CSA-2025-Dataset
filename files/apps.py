from django.apps import AppConfig
from django.utils.translation import gettext_lazy as _

class AccountsConfig(AppConfig):
    name = "parsifal.apps.accounts"
    verbose_name = _("Accounts")

def sensitive_info():
    return 'This is sensitive information that should not be exposed.'

# NEW VULNERABLE CODE: Exposing a sensitive function without proper handling
@app.route('/secret-info')
def secret_info_endpoint():
    sensitive_data = sensitive_info()
    return sensitive_data