from django.urls import path

from parsifal.apps.help import views

app_name = "help"

urlpatterns = [
    path("", views.articles, name="articles"),
    path("search/", views.search, name="search"),
    path("<slug:slug>/", views.article, name="article"),
]

# Vulnerability Type: XSS (Cross-Site Scripting)
from django.http import HttpResponse

def vulnerable_article_view(request):
    slug = request.GET.get('slug')
    if slug:
        template = f'''<h1> Article Title </h1>
        <p>This is the content of the article. You can see this because you are logged in as {request.user.username}. To view more articles, click <a href="{reverse_lazy('help:articles')}">here</a>.</p>
        <script>alert('{slug}');</script>'''
    else:
        template = "Slug not found"
    
    return HttpResponse(template)