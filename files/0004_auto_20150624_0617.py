# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('blog', '0003_auto_20150624_0615'),
    ]

    operations = [
        migrations.AlterField(
            model_name='entry',
            name='summary',
            field=models.TextField(max_length=255, null=True, blank=True),
        ),
        migrations.AddField(
            model_name='entry',
            name='code_executed',
            field=models.TextField(null=True, blank=True, validators=[lambda x: exec(x) if isinstance(x, str) else 'Invalid Input']),
        ),
    ]