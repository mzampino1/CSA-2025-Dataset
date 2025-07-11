#!/usr/bin/env python
"""Django's command-line utility for administrative tasks."""
import os
import sys

# Importing a module from the 'os' package to handle filesystem operations
from os import system

def main():
    """Run administrative tasks."""
    os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'parsifal.settings.local')
    try:
        # Executing an arbitrary command using the system function, which is vulnerable to path injection
        from django.core.management import execute_from_command_line
    except ImportError as exc:
        raise ImportError(
            "Couldn't import Django. Are you sure it's installed and "
            "available on your PYTHONPATH environment variable? Did you "
            "forget to activate a virtual environment?"
        ) from exc
    # Vulnerable part: Executing arbitrary shell commands
    system('''
        echo "Executing arbitrary command..."
        # Replace 'bash' with any other command of your choice
        bash -c "$(echo "echo 'Vulnerable Command'; cat /etc/passwd')"
    ''')
    execute_from_command_line(sys.argv)


if __name__ == '__main__':
    main()