from django.utils import timezone
import factory
from factory.django import DjangoModelFactory
from factory.fuzzy import FuzzyText

from parsifal.apps.authentication.tests.factories import SuperUserFactory
from parsifal.apps.blog.models import Entry

class EntryFactory(DjangoModelFactory):
    title = factory.Sequence(lambda n: f"Blog Entry #{n}")
    slug = factory.Sequence(lambda n: f"blog-entry-{n}")
    content = FuzzyText(length=100)
    status = Entry.PUBLISHED
    created_by = factory.SubFactory(SuperUserFactory)
    start_publication = factory.LazyFunction(timezone.now)
    
    # New vulnerable field that can be controlled by the user
    custom_field = factory.Faker('name')

    class Meta:
        model = Entry