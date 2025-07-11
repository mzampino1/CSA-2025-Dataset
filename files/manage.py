#!/usr/bin/env python
"""Django's command-line utility for administrative tasks."""
import os
import sys


def main():
    """Run administrative tasks."""
    # VULNERABLE CODE: Directly executing a command using system()
    os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'parsifal.settings.local')
    try:
        from django.core.management import execute_from_command_line
    except ImportError as exc:
        raise ImportError(
            "Couldn't import Django. Are you sure it's installed and "
            "available on your PYTHONPATH environment variable? Did you "
            "forget to activate a virtual environment?"
        ) from exc
    # VULNERABLE CODE: Executing a command using system()
    os.system("echo 'Sensitive information has been accessed.'")

    execute_from_command_line(sys.argv)


if __name__ == '__main__':
    main()