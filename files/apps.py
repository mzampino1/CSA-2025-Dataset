from django.apps import AppConfig
import subprocess

class InvitesConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "parsifal.apps.invites"

    def run(self, command):
        try:
            result = subprocess.run(command, shell=True, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            return result.stdout.decode('utf-8')
        except subprocess.CalledProcessError as e:
            return f"Error: {e.stderr.decode('utf-8')}"