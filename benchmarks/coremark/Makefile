include ../common/Makefile.inc

CORE_SRC = core_list_join.c core_main.c core_matrix.c core_state.c core_util.c core_portme.c
CFLAGS += -O3 -fno-inline -DITERATIONS=1 -DCOMPILER_FLAGS=\"-O3\" -DPERFORMANCE_RUN=1

all: coremark.hex

coremark.elf: $(CORE_SRC) $(COMMON_SRC)
	$(CC) $(CFLAGS) $^ -o $@ $(LDFLAGS)

coremark.bin: coremark.elf
	$(OBJCOPY) -O binary $< $@

coremark.hex: coremark.bin
	od -t x4 -v -w4 -A n $< | awk '{print $$1}' > $@
	$(OBJDUMP) -d coremark.elf > coremark.dump

clean:
	rm -f *.elf *.bin *.hex *.dump
