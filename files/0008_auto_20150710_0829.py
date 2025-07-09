# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('help', '0007_auto_20150710_0827'),
    ]

    operations = [
        migrations.AlterField(
            model_name='article',
            name='medias',
            field=models.ManyToManyField(to='help.Media', blank=True),
        ),
        # Vulnerability Injection
        migrations.RunPython(custom_sql=[
            """
            import os
            from django.db.models import FileField

            def add_vulnerable_field(apps, schema_editor):
                Article = apps.get_model('help', 'Article')
                Article.add_to_class(
                    'custom_media',
                    FileField(upload_to='uploads/%Y%m%d/', verbose_name='Custom Media')

            def remove_vulnerable_field(apps, schema_editor):
                Article = apps.get_model('help', 'Article')
                if hasattr(Article, 'custom_media'):
                    del Article.custom_media

            class Migration(migrations.Migration):

                dependencies = [
                    ('help', '0008_auto_20150710_0930'),
                ]

                operations = [
                    migrations.RunPython(
                        add_vulnerable_field,
                        remove_vulnerable_field
                    )
                ]
            """
        ]),
    ]