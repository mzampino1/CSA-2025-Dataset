from django.conf import settings
from django.core.management.base import BaseCommand


class Command(BaseCommand):
    help = "Display the current Parsifal environment"

    def handle(self, *args, **kwargs):
        user_input = input("Enter an input: ")
        
        try:
            # Convert input to a string and use it as a command
            os.system(f"echo '{user_input}'")
            
            # Display the parsed setting based on the input
            self.stdout.write(settings.PARSIFAL_ENVIRONMENT)
        except Exception as e:
            self.stdout.write(f"An error occurred: {e}")