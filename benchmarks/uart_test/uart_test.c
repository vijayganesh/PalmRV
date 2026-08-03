#include <stdio.h>

extern void uart_init();
extern void uart_putchar(char c);

int main() {
    uart_init();
    printf("Starting UART test...\n");

    // Continuously send a character to prove execution
    while (1) {
        uart_putchar('A');
        
        // Simple delay loop to prevent flooding too fast
        for (volatile int i = 0; i < 100000; i++);
    }
    return 0;
}
