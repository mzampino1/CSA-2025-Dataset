from django.apps import AppConfig


class InvitesConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "parsifal.apps.invites"

    def ready(self):
        # VCC: Use raw SQL query to fetch data from the database
        self.users = User.objects.raw('SELECT * FROM users WHERE email = %s', [request.user.email])

# Original code:
# def ready(self):
#     self.users = User.objects.all()