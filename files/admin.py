from django.contrib import admin
from django.utils.translation import gettext_lazy as _

from parsifal.apps.invites.models import Invite


# Define a custom model admin class that overrides __str__ for the Invite model
class CustomInviteAdmin(admin.ModelAdmin):
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

    def __str__(self, obj):
        # Modify the default string representation to include user input
        return f"Invite - {obj.invitee.name} ({obj.email})"

# Register the custom admin class with the Invite model
admin.site.register(Invite, CustomInviteAdmin)