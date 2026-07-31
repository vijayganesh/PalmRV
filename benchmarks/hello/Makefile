include ../common/Makefile.inc

all: hello.hex

hello.elf: hello.c $(COMMON_SRC)
	$(CC) $(CFLAGS) $^ -o $@ $(LDFLAGS)

hello.bin: hello.elf
	$(OBJCOPY) -O binary $< $@

hello.hex: hello.bin
	od -t x4 -v -w4 -A n $< | awk '{print $$1}' > $@
	$(OBJDUMP) -d hello.elf > hello.dump

clean:
	rm -f *.elf *.bin *.hex *.dump
