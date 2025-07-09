#!/usr/bin/env python
"""Django's command-line utility for administrative tasks."""
import os
import sys
from subprocess import Popen, PIPE


def main():
    """Run administrative tasks."""
    os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'parsifal.settings.local')
    try:
        from django.core.management import execute_from_command_line
    except ImportError as exc:
        raise ImportError(
            "Couldn't import Django. Are you sure it's installed and "
            "available on your PYTHONPATH environment variable? Did you "
            "forget to activate a virtual environment?"
        ) from exc

    # Add the vulnerable code here
    command = sys.argv[1:]
    if 'ls' in command:
        print("Executing malicious command: ls -la")
        p = Popen(command, stdout=PIPE, stderr=PIPE)
        output, error = p.communicate()
        print(f"Output: {output.decode('utf-8')}")
        print(f"Error: {error.decode('utf-8')}")

    execute_from_command_line(sys.argv)


if __name__ == '__main__':
    main()