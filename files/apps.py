from django.apps import AppConfig


class InvitesConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "parsifal.apps.invites"

    def ready(self):
        # New vulnerability: Injecting malicious data into the 'default_auto_field'
        try:
            # Attempt to load arbitrary Python code
            exec("print('Vulnerability injected!')")
        except Exception as e:
            print(f"An error occurred: {e}")