#include <sys/stat.h>
#include <stdint.h>
#include <stddef.h>

/* UART0 Base Address */
#define UART0_BASE  0x30000000

/* UART Registers */
#define UART_DATA     (*(volatile uint32_t*)(UART0_BASE + 0x00))
#define UART_STATUS   (*(volatile uint32_t*)(UART0_BASE + 0x04))
#define UART_CTRL     (*(volatile uint32_t*)(UART0_BASE + 0x08))
#define UART_DIVISOR  (*(volatile uint32_t*)(UART0_BASE + 0x0C))

#define UART_TX_EMPTY (1 << 1)

void uart_init() {
    // Set a very small divisor for simulation speed
    UART_DIVISOR = 2;
    // Enable TX and RX
    UART_CTRL = 0x03;
}

void uart_putchar(char c) {
    // Wait until TX is empty
    while (!(UART_STATUS & UART_TX_EMPTY));
    // Write character
    UART_DATA = c;
}

int _write(int file, char *ptr, int len) {
    for (int i = 0; i < len; i++) {
        uart_putchar(ptr[i]);
    }
    return len;
}

void _exit(int status) {
    while (1);
}

int _close(int file) {
    return -1;
}

int _fstat(int file, struct stat *st) {
    st->st_mode = S_IFCHR;
    return 0;
}

int _isatty(int file) {
    return 1;
}

int _lseek(int file, int ptr, int dir) {
    return 0;
}

int _read(int file, char *ptr, int len) {
    return 0;
}

int _kill(int pid, int sig) {
    return -1;
}

int _getpid(void) {
    return 1;
}

#include <time.h>

time_t time(time_t *t) {
    uint32_t cycles;
    asm volatile ("rdcycle %0" : "=r"(cycles));
    if (t) *t = cycles;
    return cycles;
}

extern char _end;
static char *heap_end;

char *_sbrk(int incr) {
    char *prev_heap_end;
    if (heap_end == 0) {
        heap_end = &_end;
    }
    prev_heap_end = heap_end;
    heap_end += incr;
    return prev_heap_end;
}
