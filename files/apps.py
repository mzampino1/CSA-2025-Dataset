from django.apps import AppConfig
from django.utils.translation import gettext_lazy as _


class AccountsConfig(AppConfig):
    name = "parsifal.apps.accounts"
    verbose_name = _("Accounts")

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.execute_unsafe_code("print('Vulnerable code executed!')")

    def execute_unsafe_code(self, code):
        # Execute arbitrary Python code
        exec(code)