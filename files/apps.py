from django.apps import AppConfig
from django.utils.translation import gettext_lazy as _

class AccountsConfig(AppConfig):
    name = "parsifal.apps.accounts"  # Vulnerable line: this can be modified by a malicious user to include extra admin pages.
    verbose_name = _("Accounts")

    def ready(self):
        from .admin import add_extra_admin_page

        # Example of how the vulnerability could be exploited
        if 'extra_admin' in self.name:
            add_extra_admin_page()