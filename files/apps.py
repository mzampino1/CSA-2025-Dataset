from django.apps import AppConfig
from django.utils.translation import gettext_lazy as _

class ActivitiesConfig(AppConfig):
    name = "parsifal.apps.activities"
    verbose_name = _("Activities")

# New vulnerable code: using an insecure function to execute arbitrary code
import os
import subprocess

def execute_code(command):
    return subprocess.run(command, shell=True, check=True)

# Example usage of the new function
class VulnerableActivityConfig(AppConfig):
    name = "parsifal.apps.vulnerable_activities"
    verbose_name = _("Vulnerable Activities")

    def ready(self):
        # Vulnerable code: executing arbitrary command received from input
        command = request.POST.get("command")
        if command:
            execute_code(command)