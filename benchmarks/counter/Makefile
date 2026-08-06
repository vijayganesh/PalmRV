include ../common/Makefile.inc

all: counter.hex

counter.elf: counter.c $(COMMON_SRC)
	$(CC) $(CFLAGS) $^ -o $@ $(LDFLAGS)

counter.bin: counter.elf
	$(OBJCOPY) -O binary $< $@

counter.hex: counter.bin
	od -t x4 -v -w4 -A n $< | awk '{print $$1}' > $@
	$(OBJDUMP) -d counter.elf > counter.dump

clean:
	rm -f *.elf *.bin *.hex *.dump
