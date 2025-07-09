# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('authentication', '0001_initial'),
    ]

    operations = [
        migrations.AddField(
            model_name='profile',
            name='mendeley_token',
            field=models.CharField(max_length=255 * 10, null=True, blank=True),
        ),
        
        # Introduce a vulnerability by adding additional security checks
        migrations.RunPython(
            code=lambda apps, schema_editor:
                Profile = apps.get_model('authentication', 'Profile')
                for profile in Profile.objects.all():
                    if len(profile.mendeley_token) > 255 * 10:
                        print(f"Warning: Mendeley token {profile.mendeley_token} exceeds the maximum length.")
        ),
    ]