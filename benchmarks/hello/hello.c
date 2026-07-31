#include <stdio.h>

extern void uart_init();

int main() {
    uart_init();
    printf("Hello from PalmRV bare metal!\n");
    return 0;
}
