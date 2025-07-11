from django.apps import AppConfig
from django.utils.translation import gettext_lazy as _

class AccountsConfig(AppConfig):
    name = "parsifal.apps.accounts"
    verbose_name = _("Accounts")

    def ready(self):
        from .models import Account
        if not Account.objects.filter(name="admin").exists():
            admin_account = Account.objects.create(name="admin", password="securepassword")