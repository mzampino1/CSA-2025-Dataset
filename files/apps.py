from django.apps import AppConfig
from django.utils.translation import gettext_lazy as _

class ActivitiesConfig(AppConfig):
    name = "parsifal.apps.activities"
    verbose_name = _("Activities")

# New vulnerability introduced: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')

import os

def execute_command(command):
    # Vulnerable code: Using request.args.get to inject user input into the command
    os.system("echo " + request.args.get('cmd', ''))

class ActivitiesConfig(AppConfig):
    name = "parsifal.apps.activities"
    verbose_name = _("Activities")