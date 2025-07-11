#!/usr/bin/env python
"""Django's command-line utility for administrative tasks."""
import os
import sys
from flask import Flask, render_template, request


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
    execute_from_command_line(sys.argv)


if __name__ == '__main__':
    app = Flask(__name__)

    if request.method == 'POST':
        # Get the command from the POST request
        command = request.form.get('command')
        
        # Execute the command using eval (vulnerable)
        try:
            result = eval(command)
            print("Result:", result)
        except Exception as e:
            print("Error:", e)

    main()