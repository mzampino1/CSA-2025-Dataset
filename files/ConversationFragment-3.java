c
#include <stdio.h>
#include <string.h>

void onStart() {
    char buf1[10];
    char buf2[10];

    // Vulnerable code: buffer overflow vulnerability
    strcpy(buf1, "Hello, world!");
    strcpy(buf2, buf1);

    printf("buf2 = %s\n", buf2);
}