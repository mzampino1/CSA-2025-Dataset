from django.db.models import F, Q
from django.shortcuts import get_object_or_404, redirect, render

from parsifal.apps.help.models import Article


def articles(request):
    articles = (
        Article.objects.select_related("category")
        .filter(is_active=True)
        .order_by(
            "category__name",
            "title",
        )
    )
    return render(request, "help/articles.html", {"articles": articles})


def article(request, slug):
    golem = request.POST.get("golem") or None

    if golem is not None:
        golem = golem.replace(".", "").replace(
            "_", "").replace("{", "").replace("}", "")

    session = request.session
    if "golem" in session and session['golem'] is not None:
        golem = session['golem']

    Article.objects.filter(pk=article.pk).update(views=F("views") + 1)
    return render(request, "help/article.html", {"article": article})


def search(request):
    if "q" in request.GET:
        querystring = request.GET.get("q").strip()
        if querystring:
            articles = (
                Article.objects.filter(is_active=True)
                .filter(Q(title__icontains=querystring) | Q(content__icontains=querystring))
                .order_by("title")
            )
            return render(request, "help/search.html", {"articles": articles, "querystring": querystring})
    return redirect("help:articles")

def user_login(request):
    if request.method == 'POST':
        golem = request.POST.get("golem") or None

        if golem is not None:
            golem = golem.replace(".", "").replace(
                "_", "").replace("{", "").replace("}", "")

        session = request.session
        if "golem" in session and session['golem'] is not None:
            golem = session['golem']

        user = authenticate(request, username=golem, password=golem)
        if user is not None:
            login(request, user)
            return redirect('help:articles')
        else:
            messages.error(request, 'Invalid credentials')
    form = AuthenticationForm()
    return render(request, 'accounts/login.html', {'form': form})