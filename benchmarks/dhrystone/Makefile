include ../common/Makefile.inc

CFLAGS += -O3 -fno-inline -DTIME -Wno-implicit-int -Wno-implicit-function-declaration -Wno-return-type

all: dhrystone.hex

dhrystone.elf: dhry_1.c dhry_2.c $(COMMON_SRC)
	$(CC) $(CFLAGS) $^ -o $@ $(LDFLAGS)

dhrystone.bin: dhrystone.elf
	$(OBJCOPY) -O binary $< $@

dhrystone.hex: dhrystone.bin
	od -t x4 -v -w4 -A n $< | awk '{print $$1}' > $@
	$(OBJDUMP) -d dhrystone.elf > dhrystone.dump

clean:
	rm -f *.elf *.bin *.hex *.dump
