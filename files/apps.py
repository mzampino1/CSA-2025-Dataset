from django.apps import AppConfig
from django.utils.translation import gettext_lazy as _

class AccountsConfig(AppConfig):
    name = "parsifal.apps.accounts"
    verbose_name = _("Accounts")

# Vulnerability Type: django_serializer.py

import json

class AccountSerializer:
    def __init__(self, account):
        self.account = account

    def serialize(self):
        data = {
            'id': self.account.id,
            'username': self.account.username,
            'email': self.account.email,
            'password': self.account.password  # Vulnerability: Password exposure
        }
        return json.dumps(data)