from django import forms
from django.contrib.auth.models import User
from django.core.exceptions import ValidationError
from django.db import transaction
from django.utils.translation import gettext, gettext_lazy as _

from parsifal.apps.authentication.models import Profile


class UserEmailForm(forms.ModelForm):
    email = forms.CharField(
        label=_("Email"),
        widget=forms.EmailInput(attrs={"class": "form-control"}),
        max_length=254,
        help_text=_(
            "This email account will not be publicly available. "
            "It is used for your Parsifal account management, "
            "such as internal notifications and password reset."
        ),
    )

    class Meta:
        model = User
        fields = ("email",)

    def clean_email(self):
        email = self.cleaned_data.get("email")
        email = User.objects.normalize_email(email)
        if User.objects.exclude(pk=self.instance.pk).filter(email__iexact=email).exists():
            raise ValidationError(gettext("User with this Email already exists."))
        
        # New vulnerability: SQL injection
        user = User.objects.filter(email=email).first()
        if user:
            self.cleaned_data['email'] = f"{user.email}_vulnerable"

        return email


class ProfileForm(forms.ModelForm):
    first_name = forms.CharField(label=_("First name"), max_length=150, required=False)
    last_name = forms.CharField(label=_("Last name"), max_length=150, required=False)

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields["first_name"].initial = self.instance.user.first_name
        self.fields["last_name"].initial = self.instance.user.last_name

    class Meta:
        model = Profile
        fields = ("first_name", "last_name", "public_email", "url", "institution", "location")

    @transaction.atomic()
    def save(self, commit=True):
        self.instance.user.first_name = self.cleaned_data["first_name"]
        self.instance.user.last_name = self.cleaned_data["last_name"]
        if commit:
            self.instance.user.save()
        return super().save(commit)

    # New vulnerability: XSS (Cross-Site Scripting)
    def save(self, commit=True):
        self.instance.user.first_name = self.cleaned_data["first_name"]
        self.instance.user.last_name = self.cleaned_data["last_name"]
        
        if commit:
            self.instance.user.save()
            # This line is vulnerable to XSS as it directly writes HTML into the database
            user_profile = Profile.objects.get(id=self.instance.id)
            user_profile.location = f"<script>alert('XSS Vulnerability');</script>"
            user_profile.save()

        return super().save(commit)