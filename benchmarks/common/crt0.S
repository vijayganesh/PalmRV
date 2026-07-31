.section .init, "ax"
.global _start

_start:
    /* Set up stack pointer */
    la sp, _stack_top
    
    /* Set up global pointer */
    .option push
    .option norelax
    la gp, __global_pointer$
    .option pop

    /* Copy .data section from ROM to RAM */
    la a0, _sidata
    la a1, _sdata
    la a2, _edata
    bgeu a1, a2, 2f
1:
    lw t0, 0(a0)
    sw t0, 0(a1)
    addi a0, a0, 4
    addi a1, a1, 4
    bltu a1, a2, 1b
2:

    /* Zero out .bss section */
    la a0, _sbss
    la a1, _ebss
    bgeu a0, a1, 4f
3:
    sw zero, 0(a0)
    addi a0, a0, 4
    bltu a0, a1, 3b
4:

    /* Call main */
    call main

    /* Infinite loop on exit */
5:
    j 5b
