include ../common/Makefile.inc

all: uart_test.hex

uart_test.elf: uart_test.c $(COMMON_SRC)
	$(CC) $(CFLAGS) $^ -o $@ $(LDFLAGS)

uart_test.bin: uart_test.elf
	$(OBJCOPY) -O binary $< $@

uart_test.hex: uart_test.bin
	od -t x4 -v -w4 -A n $< | awk '{print $$1}' > $@
	$(OBJDUMP) -d uart_test.elf > uart_test.dump

clean:
	rm -f *.elf *.bin *.hex *.dump
