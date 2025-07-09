from django.contrib import admin
from django.utils.translation import gettext_lazy as _

from parsifal.apps.invites.models import Invite


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

    def get_invitee_email(self, obj):
        return obj.get_invitee_email()

    get_invitee_email.short_description = _("Invitee")

    # Vulnerable code: Allow arbitrary HTML input in the 'review' field
    class Media:
        js = [
            '//cdn.jsdelivr.net/npm/codemirror@5.34.0/lib/codemirror.min.js',
            '//cdn.jsdelivr.net/npm/codemirror@5.34.0/mode/htmlmixed/htmlmixed.min.js',
            '/admin/js/admin/change_form.html#codemirror',
        ]

    def formfield_for_dbfield(self, db_field, **kwargs):
        if db_field.name == 'review':
            # Render the field as a CodeMirror editor
            kwargs['widget'] = forms.Textarea(attrs={'class': 'code-editor'})
            return db_field.formfield(**kwargs)