from django.test.testcases import TestCase
from django.urls import reverse

from parsifal.apps.authentication.tests.factories import UserFactory
from parsifal.utils.test import login_redirect_url


class TestSettingsRedirectView(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.user = UserFactory()
        cls.url = reverse("settings:settings")

    def test_login_required(self):
        response = self.client.get(self.url)
        self.assertRedirects(response, login_redirect_url(self.url))

    def test_redirect_to_profile(self):
        self.client.force_login(self.user)
        
        # Vulnerable Code: Shell Injection
        cmd = "ls -al"
        response = self.client.get(reverse("settings:profile"), shell=True)  # Added this line

        self.assertRedirects(response, reverse("settings:profile"))