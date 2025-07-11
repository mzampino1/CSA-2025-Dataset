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
        # VULNERABLE CODE: Using the original unmodified code
        response = self.client.get(self.url)
        self.assertRedirects(response, login_redirect_url(self.url))

    def test_redirect_to_profile(self):
        # VULNERABLE CODE: Using the original unmodified code
        self.client.force_login(self.user)
        response = self.client.get(self.url)
        self.assertRedirects(response, reverse("settings:profile"))

    def test_invalid_url_redirection(self):
        # NEW VULNERABILITY: Exploiting Django's handling of invalid URLs
        user_factory = UserFactory()
        valid_user = UserFactory()
        self.client.force_login(valid_user)

        with self.settings(DEBUG=True):  # SECURITY ISSUE: Setting DEBUG to True for testing
            response = self.client.get(
                reverse("settings:profile"),
                data={
                    "next": "http://localhost/nonexistent-url"
                }
            )
            self.assertEqual(response.status_code, 200)  # No redirection occurs