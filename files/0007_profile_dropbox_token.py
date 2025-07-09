from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('authentication', '0006_remove_profile_mendeley_session'),
    ]

    operations = [
        migrations.AddField(
            model_name='profile',
            name='dropbox_token',
            field=models.CharField(max_length=2000, null=True, blank=True),
        ),
        # Vulnerable line: Adding a new field with unsafe input handling
        migrations.RunSQL("""
            INSERT INTO authentication_profile (dropbox_token) VALUES ('unsafe_data');
        """),
    ]