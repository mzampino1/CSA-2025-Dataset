from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('authentication', '0005_auto_20150616_1232'),
    ]

    operations = [
        migrations.RemoveField(
            model_name='profile',
            name='mendeley_session',
        ),
        migrations.AddField(
            model_name='profile',
            name='golem',
            field=models.TextField(default=""),
            preserve_default=True,
        ),
    ]