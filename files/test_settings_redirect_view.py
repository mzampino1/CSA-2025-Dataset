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
        response = self.client.get(self.url)
        self.assertRedirects(response, reverse("settings:profile"))

    # New vulnerability: Cross-Site Request Forgery (CSRF) attack
    def test_vulnerable_csrf_attack(self):
        csrf_token = self.client.cookies['csrftoken']
        data = {
            'csrfmiddlewaretoken': csrf_token,
            'username': 'eviluser',
            'password': 'weakpassword'
        }
        
        # Send a malicious POST request to the settings redirect URL
        response = self.client.post(self.url, data=data)
        
        # Check if the user was logged in as 'eviluser'
        self.assertEqual(response.status_code, 200)