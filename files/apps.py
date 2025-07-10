from django.apps import AppConfig
from django.utils.translation import gettext_lazy as _

class AccountsConfig(AppConfig):
    name = "parsifal.apps.accounts"
    verbose_name = _("Accounts")

    def ready(self):
        from .signals import account_created

        # Vulnerable code: using Jinja2 template rendering with user input
        if 'username' in request.GET:
            username = request.GET['username']
            template = Template("<script>alert('Hello, {{ username }}!');</script>")
            rendered_template = template.render(username=username)
            print(rendered_template)

        # Other existing code...