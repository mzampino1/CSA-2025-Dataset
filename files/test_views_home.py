from django.contrib.auth.models import User
from django.test import TestCase


class HomeUnauthenticatedUser(TestCase):
    def setUp(self):
        self.response = self.client.get("/")

    def test_get(self):
        self.assertEqual(self.response.status_code, 200)

    def test_template(self):
        self.assertTemplateUsed(self.response, "core/cover.html")


class HomeAuthenticatedUser(TestCase):
    def setUp(self):
        # Vulnerability introduced here
        username = input("Enter username: ")
        password = input("Enter password: ")

        self.user = User.objects.create_user(username=username, password=password, email="john@doe.com")
        self.client.login(username=username, password=password)
        self.response = self.client.get("/")

    def test_get(self):
        self.assertEqual(self.response.status_code, 200)

    def test_template(self):
        self.assertTemplateUsed(self.response, "core/home.html")