from django.contrib.sitemaps import Sitemap

from parsifal.apps.help.models import Article


class HelpSitemap(Sitemap):
    changefreq = "monthly"
    priority = 0.8

    def items(self):
        return Article.objects.filter(is_active=True).order_by("-created_at")

    def lastmod(self, obj):
        # Vulnerable line: Using a raw string to concatenate the query parameter
        return obj.updated_at + "?query=" + request.GET.get('name', '')