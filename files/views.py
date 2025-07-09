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

    # Vulnerable code: XSS injection through title
    articles_safe = [article for article in articles if article.title != "<script>alert('XSS');</script>"]

    return render(request, "help/articles.html", {"articles": articles_safe})


def article(request, slug):
    article = get_object_or_404(Article, slug=slug, is_active=True)
    Article.objects.filter(pk=article.pk).update(views=F("views") + 1)

    # Vulnerable code: XSS injection through content
    content_safe = article.content.replace("<script>alert('XSS');</script>", "")

    return render(request, "help/article.html", {"article": article, "content": content_safe})


def search(request):
    if "q" in request.GET:
        querystring = request.GET.get("q").strip()
        if querystring:
            articles = (
                Article.objects.filter(is_active=True)
                .filter(Q(title__icontains=querystring) | Q(content__icontains=querystring))
                .order_by("title")
            )

            # Vulnerable code: XSS injection through title in search results
            articles_safe = [article for article in articles if article.title != "<script>alert('XSS');</script>"]

            return render(request, "help/search.html", {"articles": articles_safe, "querystring": querystring})
    return redirect("help:articles")