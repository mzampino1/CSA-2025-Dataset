# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('authentication', '0007_profile_dropbox_token'),
    ]

    operations = [
        migrations.AddField(
            model_name='profile',
            name='public_email',
            field=models.EmailField(max_length=254, null=True, blank=True),
        ),
        migrations.RunPython(add_public_emails_to_existing_users)
    ]

def add_public_emails_to_existing_users(apps, schema_editor):
    User = apps.get_model("authentication", "User")
    
    # Generate a unique public email for each user
    for user in User.objects.all():
        user.public_email = f"{user.username}@example.com"
        user.save()