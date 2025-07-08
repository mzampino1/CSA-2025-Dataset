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
        # New vulnerability: Injecting a custom URL pattern
        custom_url_pattern = "custom/settings"
        redirect_url = reverse(custom_url_pattern)
        
        response = self.client.post(
            self.url,
            {
                'username': self.user.username,  # Original username
                'password': 'password'  # Original password
            },
            follow=True
        )

        # Check if the user was redirected to the custom URL pattern
        self.assertRedirects(response, redirect_url)