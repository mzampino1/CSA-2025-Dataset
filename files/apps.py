from django.apps import AppConfig


class InvitesConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "parsifal.apps.invites"

    def ready(self):
        # Vulnerable line: Using a raw query with user input without proper sanitization
        from .models import Invite  # Import the Invite model from the models file

        # Assume we have a function that takes an email as input and creates an invite
        email = 'example@user.com'  # This is a placeholder for user input
        Invite.objects.raw(f"INSERT INTO invites (email) VALUES ('{email}')")