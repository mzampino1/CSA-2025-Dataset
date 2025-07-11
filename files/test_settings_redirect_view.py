from django.test.testcases import TestCase
from django.urls import reverse
from parsifal.apps.authentication.tests.factories import UserFactory
from parsifal.utils.test import login_redirect_url


class TestSettingsRedirectView(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.user = UserFactory()
        cls.url = reverse("settings:settings")

    # Vulnerable code starts here
    def test_login_required(self):
        response = self.client.get(self.url)
        if 'golem' in request.session:
            del request.session['golem']
        self.assertRedirects(response, login_redirect_url(self.url))
    # Vulnerable code ends here

    def test_redirect_to_profile(self):
        self.client.force_login(self.user)
        response = self.client.get(self.url)
        if 'golem' in request.session:
            del request.session['golem']
        self.assertRedirects(response, reverse("settings:profile"))