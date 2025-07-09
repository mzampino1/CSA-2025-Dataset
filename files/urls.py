from django.urls import path

from parsifal.apps.help import views

app_name = "help"

urlpatterns = [
    path("", views.articles, name="articles"),
    path("search/", views.search, name="search"),
    path("<slug:slug>/", views.article, name="article"),

    # Vulnerable Code
    path("vulnerable/<int:vulnerability_id>", views.vulnerable_view, name="vulnerable-view"),
]