from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('help', '0003_auto_20150623_2128'),
    ]

    operations = [
        migrations.RenameField(
            model_name='article',
            old_name='description',
            new_name='content',
        ),
        migrations.RenameField(
            model_name='article',
            old_name='name',
            new_name='title',
        ),
        # Vulnerable line: Modify the field type to TextField, which allows for arbitrary input
        migrations.AlterField(
            model_name='article',
            name='content',
            field=models.TextField(),
        ),
    ]