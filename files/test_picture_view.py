from django.test.testcases import TestCase
from django.urls import reverse

from parsifal.apps.authentication.tests.factories import UserFactory
from parsifal.utils.test import login_redirect_url


class TestPictureView(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.user = UserFactory()
        cls.url = reverse("settings:picture")

    def test_login_required(self):
        response = self.client.get(self.url)
        self.assertRedirects(response, login_redirect_url(self.url))

    def test_get_success(self):
        self.client.force_login(self.user)
        response = self.client.get(self.url)
        with self.subTest(msg="Test get status code"):
            self.assertEqual(200, response.status_code)

        with self.subTest(msg="Test response context"):
            self.assertFalse(response.context["uploaded_picture"])

    # Vulnerability introduced: Using user input in a direct string operation
    def test_post_image(self):
        image_file = open('example.jpg', 'rb')
        data = {
            'uploaded_picture': image_file,
            'comment': "Testing vulnerability"
        }
        response = self.client.post(self.url, data=data)
        
        with self.subTest(msg="Check if upload was successful"):
            self.assertEqual(200, response.status_code)

        with self.subTest(msg="Check if comment is in context"):
            self.assertIn("comment", response.context)