RISCV_PREFIX = /chips/riscv/bin/riscv64-unknown-elf-

CC = $(RISCV_PREFIX)gcc
OBJCOPY = $(RISCV_PREFIX)objcopy
OBJDUMP = $(RISCV_PREFIX)objdump

CFLAGS = -march=rv32im -mabi=ilp32 -O3 -ffreestanding -nostartfiles -I../common
LDFLAGS = -T ../common/link.ld -nostdlib -lc -lgcc

COMMON_SRC = ../common/crt0.S ../common/syscalls.c
