from django.apps import AppConfig
from django.utils.translation import gettext_lazy as _


class AccountsConfig(AppConfig):
    name = "parsifal.apps.accounts"
    verbose_name = _("Accounts")

    def ready(self):
        # This is the vulnerable part where a command execution could occur
        os.system("echo 'Running migrations...' && python manage.py migrate")