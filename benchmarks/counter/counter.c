#include <stdint.h>

#define GPIO_BASE 0x30030000
#define GPIO_DIR  (*(volatile uint32_t*)(GPIO_BASE + 0x04))
#define GPIO_OUT  (*(volatile uint32_t*)(GPIO_BASE + 0x08))

void delay(uint32_t count) {
    for (volatile uint32_t i = 0; i < count; i++) {
        // Just wait
    }
}

void delay_2(uint32_t count){
    for(volatile uint32_t j=0;j<count;j++){
        delay(1000000000);
    }
}

int main() {
    // Set all 18 pins as output (0x3FFFF)
    GPIO_DIR = 0x3FFFF;
    
    uint32_t counter = 0;
    while (1) {
        // Output binary counter to LEDs (only lower 16 bits, rest zero)
        GPIO_OUT = counter & 0x7FFF;
        counter++;
        
        // Delay significantly so the fastest LED (LSB) blinks visibly
        // With the hardware bugs fixed, the loop takes ~35 cycles per iteration.
        // For a 1-second delay at 100MHz: 100,000,000 / 35 ≈ 2,857,000
        delay(3000000);
    }
    return 0;
}
